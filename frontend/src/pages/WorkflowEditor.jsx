import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import api from '../services/api'
import InputSchemaBuilder from '../components/workflow/InputSchemaBuilder'

function WorkflowEditor() {
  const { id } = useParams()
  const navigate = useNavigate()
  const isEditMode = !!id

  // Workflow state
  const [workflow, setWorkflow] = useState({
    name: '',
    description: '',
    triggerType: 'MANUAL',
    executionMode: 'SYNC',
    isActive: false,
    triggerConfigJson: {},
    inputSchemaJson: null,
    interfaceType: 'FORM',
    isPublic: false
  })

  // Steps state
  const [steps, setSteps] = useState([])
  const [agents, setAgents] = useState([])
  const [showStepForm, setShowStepForm] = useState(false)
  const [editingStep, setEditingStep] = useState(null)
  const [stepForm, setStepForm] = useState({
    stepOrder: 1,
    stepType: 'AGENT_EXECUTION',
    name: '',
    agentId: null,
    inputMappingJson: {},
    outputVariable: '',
    conditionExpression: '',
    timeoutSeconds: 300
  })

  // UI state
  const [loading, setLoading] = useState(isEditMode)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    loadAgents()
    if (isEditMode) {
      loadWorkflow()
    }
  }, [id])

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

  const handleAddStep = () => {
    setStepForm({
      stepOrder: steps.length + 1,
      stepType: 'AGENT_EXECUTION',
      name: '',
      agentId: null,
      inputMappingJson: {},
      outputVariable: '',
      conditionExpression: '',
      timeoutSeconds: 300
    })
    setEditingStep(null)
    setShowStepForm(true)
  }

  const handleEditStep = (step) => {
    setStepForm({
      stepOrder: step.stepOrder,
      stepType: step.stepType,
      name: step.name,
      agentId: step.agentId,
      inputMappingJson: step.inputMappingJson || {},
      outputVariable: step.outputVariable || '',
      conditionExpression: step.conditionExpression || '',
      timeoutSeconds: step.timeoutSeconds || 300
    })
    setEditingStep(step)
    setShowStepForm(true)
  }

  const handleSaveStep = async () => {
    try {
      if (!stepForm.name.trim()) {
        alert('Step name is required')
        return
      }

      if (stepForm.stepType === 'AGENT_EXECUTION' && !stepForm.agentId) {
        alert('Agent is required for AGENT_EXECUTION steps')
        return
      }

      if (editingStep) {
        // Update existing step
        await api.updateWorkflowStep(id, editingStep.id, stepForm)
      } else {
        // Create new step
        await api.createWorkflowStep(id, stepForm)
      }

      // Reload steps
      const stepsRes = await api.getWorkflowSteps(id)
      setSteps(stepsRes.data)
      setShowStepForm(false)
    } catch (error) {
      console.error('Error saving step:', error)
      alert('Failed to save step: ' + (error.response?.data?.message || error.message))
    }
  }

  const handleDeleteStep = async (stepId, stepName) => {
    if (!window.confirm(`Delete step "${stepName}"?`)) {
      return
    }

    try {
      await api.deleteWorkflowStep(id, stepId)
      const stepsRes = await api.getWorkflowSteps(id)
      setSteps(stepsRes.data)
    } catch (error) {
      console.error('Error deleting step:', error)
      alert('Failed to delete step')
    }
  }

  const handleInputMappingChange = (value) => {
    try {
      const parsed = value ? JSON.parse(value) : {}
      setStepForm({ ...stepForm, inputMappingJson: parsed })
    } catch (error) {
      // Invalid JSON, keep as string for now
    }
  }

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-gray-500">Loading workflow...</div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-start">
        <div>
          <h1 className="text-3xl font-bold text-gray-900 mb-2">
            {isEditMode ? 'Edit Workflow' : 'Create Workflow'}
          </h1>
          <p className="text-gray-600">
            {isEditMode ? `Editing: ${workflow.name}` : 'Create a new multi-agent workflow'}
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => navigate('/workflows')}
            className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
          >
            Cancel
          </button>
          {isEditMode && (
            <button
              onClick={() => {
                const url = workflow.interfaceType === 'CHAT'
                  ? `/workflow/chat/${id}`
                  : `/workflow/execute/${id}`
                window.open(url, '_blank')
              }}
              className="px-4 py-2 border border-primary-600 text-primary-600 rounded-lg hover:bg-primary-50 flex items-center gap-2"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
              </svg>
              Open Interface
            </button>
          )}
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
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded">
          {error}
        </div>
      )}

      {/* Workflow Settings */}
      <div className="card">
        <h2 className="text-xl font-semibold mb-6">Workflow Settings</h2>
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Workflow Name *
            </label>
            <input
              type="text"
              value={workflow.name}
              onChange={(e) => setWorkflow({ ...workflow, name: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
              placeholder="e.g., Customer Query Classification"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Description
            </label>
            <textarea
              value={workflow.description}
              onChange={(e) => setWorkflow({ ...workflow, description: e.target.value })}
              rows="3"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
              placeholder="Describe what this workflow does..."
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Trigger Type
              </label>
              <select
                value={workflow.triggerType}
                onChange={(e) => setWorkflow({ ...workflow, triggerType: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
              >
                <option value="MANUAL">Manual</option>
                <option value="SCHEDULED">Scheduled</option>
                <option value="EVENT">Event</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Execution Mode
              </label>
              <select
                value={workflow.executionMode}
                onChange={(e) => setWorkflow({ ...workflow, executionMode: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
              >
                <option value="SYNC">Synchronous</option>
                <option value="ASYNC">Asynchronous</option>
              </select>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Interface Type
            </label>
            <select
              value={workflow.interfaceType}
              onChange={(e) => setWorkflow({ ...workflow, interfaceType: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
            >
              <option value="FORM">Form (structured inputs)</option>
              <option value="CHAT">Chat (conversational)</option>
              <option value="API">API (programmatic)</option>
              <option value="CUSTOM">Custom</option>
            </select>
            <p className="text-xs text-gray-500 mt-1">
              How users will interact with this workflow
            </p>
          </div>

          <div className="space-y-2">
            <div className="flex items-center">
              <input
                type="checkbox"
                id="isActive"
                checked={workflow.isActive}
                onChange={(e) => setWorkflow({ ...workflow, isActive: e.target.checked })}
                className="h-4 w-4 text-primary-600 focus:ring-primary-500 border-gray-300 rounded"
              />
              <label htmlFor="isActive" className="ml-2 block text-sm text-gray-700">
                Active (workflow will be available for execution)
              </label>
            </div>

            <div className="flex items-center">
              <input
                type="checkbox"
                id="isPublic"
                checked={workflow.isPublic}
                onChange={(e) => setWorkflow({ ...workflow, isPublic: e.target.checked })}
                className="h-4 w-4 text-primary-600 focus:ring-primary-500 border-gray-300 rounded"
              />
              <label htmlFor="isPublic" className="ml-2 block text-sm text-gray-700">
                Public (allow execution without authentication)
              </label>
            </div>
          </div>

          {/* Shareable Link */}
          {isEditMode && workflow.isPublic && (
            <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
              <h4 className="text-sm font-semibold text-blue-900 mb-2">Public Workflow Link</h4>
              <div className="flex gap-2">
                <input
                  type="text"
                  readOnly
                  value={`${window.location.origin}/workflow/execute/${id}`}
                  className="flex-1 px-3 py-2 bg-white border border-blue-300 rounded-lg text-sm font-mono"
                />
                <button
                  type="button"
                  onClick={() => {
                    navigator.clipboard.writeText(`${window.location.origin}/workflow/execute/${id}`)
                    alert('Link copied to clipboard!')
                  }}
                  className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm"
                >
                  Copy
                </button>
              </div>
              <p className="text-xs text-blue-700 mt-2">
                Share this link to allow anyone to execute this workflow
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Workflow Inputs */}
      <div className="card">
        <InputSchemaBuilder
          schema={workflow.inputSchemaJson}
          onChange={(schema) => setWorkflow({ ...workflow, inputSchemaJson: schema })}
        />
      </div>

      {/* Workflow Steps */}
      {isEditMode && (
        <div className="card">
          <div className="flex justify-between items-center mb-6">
            <h2 className="text-xl font-semibold">Workflow Steps</h2>
            <button
              onClick={handleAddStep}
              className="btn-primary text-sm"
            >
              + Add Step
            </button>
          </div>

          {steps.length === 0 ? (
            <div className="text-center py-8 text-gray-500">
              No steps yet. Add your first step to get started.
            </div>
          ) : (
            <div className="space-y-4">
              {steps.map((step) => (
                <div
                  key={step.id}
                  className="border border-gray-200 rounded-lg p-4 hover:border-primary-300"
                >
                  <div className="flex justify-between items-start">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-2">
                        <span className="px-2 py-1 bg-gray-100 text-gray-700 text-xs font-semibold rounded">
                          Step {step.stepOrder}
                        </span>
                        <span className="px-2 py-1 bg-blue-100 text-blue-700 text-xs font-semibold rounded">
                          {step.stepType}
                        </span>
                        <h3 className="font-semibold text-gray-900">{step.name}</h3>
                      </div>
                      {step.agentName && (
                        <p className="text-sm text-gray-600">
                          <span className="font-medium">Agent:</span> {step.agentName}
                        </p>
                      )}
                      {step.outputVariable && (
                        <p className="text-sm text-gray-600">
                          <span className="font-medium">Output Variable:</span> {step.outputVariable}
                        </p>
                      )}
                    </div>
                    <div className="flex gap-2">
                      <button
                        onClick={() => handleEditStep(step)}
                        className="px-3 py-1 text-sm bg-primary-50 text-primary-700 rounded hover:bg-primary-100"
                      >
                        Edit
                      </button>
                      <button
                        onClick={() => handleDeleteStep(step.id, step.name)}
                        className="px-3 py-1 text-sm bg-red-50 text-red-700 rounded hover:bg-red-100"
                      >
                        Delete
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Step Form Modal */}
      {showStepForm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg max-w-2xl w-full max-h-[90vh] overflow-y-auto p-6">
            <h2 className="text-2xl font-bold mb-6">
              {editingStep ? 'Edit Step' : 'Add Step'}
            </h2>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Step Name *
                </label>
                <input
                  type="text"
                  value={stepForm.name}
                  onChange={(e) => setStepForm({ ...stepForm, name: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  placeholder="e.g., Classify customer query"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Step Order
                  </label>
                  <input
                    type="number"
                    value={stepForm.stepOrder}
                    onChange={(e) => setStepForm({ ...stepForm, stepOrder: parseInt(e.target.value) })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                    min="1"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Step Type
                  </label>
                  <select
                    value={stepForm.stepType}
                    onChange={(e) => setStepForm({ ...stepForm, stepType: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  >
                    <option value="AGENT_EXECUTION">Agent Execution</option>
                    <option value="CONDITION">Condition</option>
                    <option value="APPROVAL">Approval</option>
                    <option value="PARALLEL">Parallel</option>
                  </select>
                </div>
              </div>

              {stepForm.stepType === 'AGENT_EXECUTION' && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Agent *
                  </label>
                  <select
                    value={stepForm.agentId || ''}
                    onChange={(e) => setStepForm({ ...stepForm, agentId: parseInt(e.target.value) })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  >
                    <option value="">Select an agent...</option>
                    {agents.map((agent) => (
                      <option key={agent.id} value={agent.id}>
                        {agent.name}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Output Variable
                </label>
                <input
                  type="text"
                  value={stepForm.outputVariable}
                  onChange={(e) => setStepForm({ ...stepForm, outputVariable: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  placeholder="e.g., classification_result"
                />
                <p className="text-xs text-gray-500 mt-1">
                  Variable name to store step output for use in subsequent steps
                </p>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Input Mapping (JSON)
                </label>
                <textarea
                  value={JSON.stringify(stepForm.inputMappingJson, null, 2)}
                  onChange={(e) => handleInputMappingChange(e.target.value)}
                  rows="4"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg font-mono text-sm"
                  placeholder='{"query": "${trigger.message}"}'
                />
                <p className="text-xs text-gray-500 mt-1">
                  Map variables using ${'{'}variableName{'}'} syntax
                </p>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Condition Expression (Optional)
                </label>
                <input
                  type="text"
                  value={stepForm.conditionExpression}
                  onChange={(e) => setStepForm({ ...stepForm, conditionExpression: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  placeholder="e.g., ${result.status}==success"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Timeout (seconds)
                </label>
                <input
                  type="number"
                  value={stepForm.timeoutSeconds}
                  onChange={(e) => setStepForm({ ...stepForm, timeoutSeconds: parseInt(e.target.value) })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  min="10"
                  max="3600"
                />
              </div>
            </div>

            <div className="flex gap-3 mt-6 pt-4 border-t">
              <button
                onClick={handleSaveStep}
                className="btn-primary"
              >
                {editingStep ? 'Update Step' : 'Add Step'}
              </button>
              <button
                onClick={() => setShowStepForm(false)}
                className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      {!isEditMode && (
        <div className="card bg-blue-50 border-blue-200">
          <p className="text-sm text-blue-800">
            <strong>Note:</strong> Save the workflow first, then you can add steps to it.
          </p>
        </div>
      )}
    </div>
  )
}

export default WorkflowEditor
