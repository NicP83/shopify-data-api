import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import ReactFlow, {
  MiniMap,
  Controls,
  Background,
  useNodesState,
  useEdgesState,
  addEdge,
  MarkerType
} from 'reactflow'
import 'reactflow/dist/style.css'

import api from '../services/api'
import WorkflowStepNode from '../components/workflow/WorkflowStepNode'
import StepConfigPanel from '../components/workflow/StepConfigPanel'

const nodeTypes = {
  workflowStep: WorkflowStepNode
}

function WorkflowEditorVisual() {
  const { id } = useParams()
  const navigate = useNavigate()
  const isEditMode = !!id

  // Workflow state
  const [workflow, setWorkflow] = useState({
    name: '',
    description: '',
    triggerType: 'MANUAL',
    executionMode: 'SEQUENTIAL',
    isActive: false,
    triggerConfigJson: {}
  })

  // React Flow state
  const [nodes, setNodes, onNodesChange] = useNodesState([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const [selectedNode, setSelectedNode] = useState(null)

  // Data state
  const [steps, setSteps] = useState([])
  const [agents, setAgents] = useState([])

  // UI state
  const [loading, setLoading] = useState(isEditMode)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)
  const [viewMode, setViewMode] = useState('visual') // 'visual' or 'list'

  useEffect(() => {
    loadAgents()
    if (isEditMode) {
      loadWorkflow()
    }
  }, [id])

  useEffect(() => {
    // Convert steps to React Flow nodes and edges
    if (steps.length > 0) {
      convertStepsToNodesAndEdges(steps)
    }
  }, [steps])

  const loadAgents = async () => {
    try {
      const response = await api.getAgents()
      setAgents(response.data)
    } catch (error) {
      console.error('Error loading agents:', error)
      setError('Failed to load agents')
    }
  }

  const loadWorkflow = async () => {
    try {
      setLoading(true)
      const [workflowRes, stepsRes] = await Promise.all([
        api.getWorkflow(id),
        api.getWorkflowSteps(id)
      ])

      setWorkflow(workflowRes.data)
      setSteps(stepsRes.data)
    } catch (error) {
      console.error('Error loading workflow:', error)
      setError('Failed to load workflow')
    } finally {
      setLoading(false)
    }
  }

  const convertStepsToNodesAndEdges = (stepsData) => {
    // Sort steps by order
    const sortedSteps = [...stepsData].sort((a, b) => a.stepOrder - b.stepOrder)

    // Create nodes
    const flowNodes = sortedSteps.map((step, index) => {
      const agent = agents.find(a => a.id === step.agentId)

      return {
        id: `step-${step.id}`,
        type: 'workflowStep',
        position: { x: 250, y: index * 150 + 50 }, // Vertical auto-layout
        data: {
          ...step,
          agent: agent,
          onEdit: () => handleNodeClick(`step-${step.id}`)
        }
      }
    })

    // Create edges (sequential connections)
    const flowEdges = []
    for (let i = 0; i < flowNodes.length - 1; i++) {
      flowEdges.push({
        id: `edge-${i}`,
        source: flowNodes[i].id,
        target: flowNodes[i + 1].id,
        animated: true,
        markerEnd: {
          type: MarkerType.ArrowClosed
        }
      })
    }

    setNodes(flowNodes)
    setEdges(flowEdges)
  }

  const handleNodeClick = useCallback((nodeId) => {
    const node = nodes.find(n => n.id === nodeId)
    if (node) {
      setSelectedNode(node)
    }
  }, [nodes])

  const handleStepUpdate = async (updatedStep) => {
    try {
      const stepId = selectedNode.data.id
      await api.updateWorkflowStep(id, stepId, updatedStep)

      // Reload steps
      const stepsRes = await api.getWorkflowSteps(id)
      setSteps(stepsRes.data)
      setSelectedNode(null)
    } catch (error) {
      console.error('Error updating step:', error)
      alert('Failed to update step: ' + (error.response?.data?.message || error.message))
    }
  }

  const handleStepDelete = async () => {
    if (!window.confirm(`Delete step "${selectedNode.data.name}"?`)) {
      return
    }

    try {
      const stepId = selectedNode.data.id
      await api.deleteWorkflowStep(id, stepId)

      // Reload steps
      const stepsRes = await api.getWorkflowSteps(id)
      setSteps(stepsRes.data)
      setSelectedNode(null)
    } catch (error) {
      console.error('Error deleting step:', error)
      alert('Failed to delete step')
    }
  }

  const handleSaveWorkflow = async () => {
    try {
      setSaving(true)
      setError(null)

      if (!workflow.name.trim()) {
        setError('Workflow name is required')
        return
      }

      if (isEditMode) {
        await api.updateWorkflow(id, workflow)
      } else {
        const response = await api.createWorkflow(workflow)
        navigate(`/workflows/${response.data.id}`)
        return
      }

      alert('Workflow saved successfully')
    } catch (error) {
      console.error('Error saving workflow:', error)
      setError(error.response?.data?.message || 'Failed to save workflow')
    } finally {
      setSaving(false)
    }
  }

  const handleAddStep = async () => {
    const newStep = {
      stepOrder: steps.length + 1,
      stepType: 'AGENT_EXECUTION',
      name: 'New Step',
      agentId: agents.length > 0 ? agents[0].id : null,
      inputMappingJson: {},
      outputVariable: `step${steps.length + 1}`,
      conditionExpression: '',
      timeoutSeconds: 300
    }

    try {
      await api.createWorkflowStep(id, newStep)
      const stepsRes = await api.getWorkflowSteps(id)
      setSteps(stepsRes.data)
    } catch (error) {
      console.error('Error adding step:', error)
      alert('Failed to add step')
    }
  }

  const onConnect = useCallback(
    (params) => setEdges((eds) => addEdge({
      ...params,
      animated: true,
      markerEnd: { type: MarkerType.ArrowClosed }
    }, eds)),
    [setEdges]
  )

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-gray-500">Loading workflow...</div>
      </div>
    )
  }

  return (
    <div className="h-screen flex flex-col">
      {/* Header */}
      <div className="bg-white border-b px-6 py-4 flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">
            {isEditMode ? workflow.name : 'Create Workflow'}
          </h1>
          <p className="text-sm text-gray-600">
            {isEditMode ? 'Edit workflow configuration and steps' : 'Create a new multi-agent workflow'}
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => navigate('/workflows')}
            className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            onClick={handleSaveWorkflow}
            disabled={saving}
            className="btn-primary disabled:opacity-50"
          >
            {saving ? 'Saving...' : 'Save Workflow'}
          </button>
        </div>
      </div>

      {/* Error Message */}
      {error && (
        <div className="mx-6 mt-4 bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded">
          {error}
        </div>
      )}

      {/* Main Content */}
      <div className="flex-1 flex overflow-hidden">
        {/* Left Sidebar - Workflow Settings */}
        <div className="w-80 bg-white border-r overflow-y-auto p-4">
          <h2 className="text-lg font-semibold mb-4">Workflow Settings</h2>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Workflow Name *
              </label>
              <input
                type="text"
                value={workflow.name}
                onChange={(e) => setWorkflow({ ...workflow, name: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                placeholder="e.g., Customer Query Classification"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Description
              </label>
              <textarea
                value={workflow.description}
                onChange={(e) => setWorkflow({ ...workflow, description: e.target.value })}
                rows="3"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                placeholder="Describe what this workflow does..."
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Trigger Type
              </label>
              <select
                value={workflow.triggerType}
                onChange={(e) => setWorkflow({ ...workflow, triggerType: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
              >
                <option value="MANUAL">Manual</option>
                <option value="SCHEDULED">Scheduled</option>
                <option value="WEBHOOK">Webhook</option>
                <option value="EVENT">Event</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Execution Mode
              </label>
              <select
                value={workflow.executionMode}
                onChange={(e) => setWorkflow({ ...workflow, executionMode: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
              >
                <option value="SEQUENTIAL">Sequential</option>
                <option value="PARALLEL">Parallel</option>
                <option value="CONDITIONAL">Conditional</option>
              </select>
            </div>

            <div className="flex items-center">
              <input
                type="checkbox"
                id="isActive"
                checked={workflow.isActive}
                onChange={(e) => setWorkflow({ ...workflow, isActive: e.target.checked })}
                className="h-4 w-4 text-primary-600 border-gray-300 rounded"
              />
              <label htmlFor="isActive" className="ml-2 text-sm text-gray-700">
                Active
              </label>
            </div>

            {isEditMode && (
              <>
                <div className="pt-4 border-t">
                  <button
                    onClick={handleAddStep}
                    className="w-full btn-primary text-sm"
                  >
                    + Add Step
                  </button>
                </div>

                <div className="text-xs text-gray-500 mt-4">
                  <p><strong>Steps:</strong> {steps.length}</p>
                  <p><strong>Status:</strong> {workflow.isActive ? 'Active' : 'Inactive'}</p>
                </div>
              </>
            )}
          </div>
        </div>

        {/* Center - React Flow Canvas */}
        <div className="flex-1 bg-gray-50">
          {isEditMode && steps.length > 0 ? (
            <ReactFlow
              nodes={nodes}
              edges={edges}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              onConnect={onConnect}
              onNodeClick={(_, node) => handleNodeClick(node.id)}
              nodeTypes={nodeTypes}
              fitView
              minZoom={0.5}
              maxZoom={1.5}
            >
              <Background variant="dots" gap={12} size={1} />
              <Controls />
              <MiniMap />
            </ReactFlow>
          ) : isEditMode ? (
            <div className="flex items-center justify-center h-full">
              <div className="text-center">
                <div className="text-6xl mb-4">🤖</div>
                <h3 className="text-lg font-semibold text-gray-900 mb-2">No steps yet</h3>
                <p className="text-gray-600 mb-4">Add your first step to start building your workflow</p>
                <button
                  onClick={handleAddStep}
                  className="btn-primary"
                >
                  + Add First Step
                </button>
              </div>
            </div>
          ) : (
            <div className="flex items-center justify-center h-full">
              <div className="text-center max-w-md">
                <div className="text-6xl mb-4">📝</div>
                <h3 className="text-lg font-semibold text-gray-900 mb-2">Save workflow first</h3>
                <p className="text-gray-600">
                  Complete the workflow settings on the left and click "Save Workflow" to start adding steps
                </p>
              </div>
            </div>
          )}
        </div>

        {/* Right Sidebar - Step Configuration */}
        {selectedNode && (
          <StepConfigPanel
            step={selectedNode.data}
            agents={agents}
            onUpdate={handleStepUpdate}
            onClose={() => setSelectedNode(null)}
            onDelete={handleStepDelete}
          />
        )}
      </div>
    </div>
  )
}

export default WorkflowEditorVisual
