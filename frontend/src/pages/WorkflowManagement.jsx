import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'

function WorkflowManagement() {
  const navigate = useNavigate()
  const [workflows, setWorkflows] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [filter, setFilter] = useState('all') // 'all', 'active', 'inactive'

  useEffect(() => {
    loadWorkflows()
  }, [filter])

  const loadWorkflows = async () => {
    try {
      setLoading(true)
      setError(null)

      const activeOnly = filter === 'active' ? true : undefined
      const response = await api.getWorkflows(activeOnly)
      setWorkflows(response.data)
    } catch (error) {
      console.error('Error loading workflows:', error)
      setError('Failed to load workflows')
    } finally {
      setLoading(false)
    }
  }

  const handleActivateWorkflow = async (id) => {
    try {
      await api.activateWorkflow(id)
      loadWorkflows()
    } catch (error) {
      console.error('Error activating workflow:', error)
      setError('Failed to activate workflow')
    }
  }

  const handleDeactivateWorkflow = async (id) => {
    try {
      await api.deactivateWorkflow(id)
      loadWorkflows()
    } catch (error) {
      console.error('Error deactivating workflow:', error)
      setError('Failed to deactivate workflow')
    }
  }

  const handleDeleteWorkflow = async (id, name) => {
    if (!window.confirm(`Are you sure you want to delete workflow "${name}"?`)) {
      return
    }

    try {
      await api.deleteWorkflow(id)
      loadWorkflows()
    } catch (error) {
      console.error('Error deleting workflow:', error)
      setError('Failed to delete workflow')
    }
  }

  const handleExecuteWorkflow = async (id, name) => {
    if (!window.confirm(`Execute workflow "${name}"?`)) {
      return
    }

    try {
      const response = await api.executeWorkflow(id, {})
      if (response.data.success) {
        alert(`Workflow executed successfully!\n\nContext: ${JSON.stringify(response.data.context, null, 2)}`)
      } else {
        alert(`Workflow failed: ${response.data.error}`)
      }
    } catch (error) {
      console.error('Error executing workflow:', error)
      alert(`Failed to execute workflow: ${error.response?.data?.error || error.message}`)
    }
  }

  const filteredWorkflows = workflows.filter(workflow => {
    if (filter === 'active') return workflow.isActive
    if (filter === 'inactive') return !workflow.isActive
    return true
  })

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-gray-500">Loading workflows...</div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-start">
        <div>
          <h1 className="text-3xl font-bold text-gray-900 mb-2">Workflow Management</h1>
          <p className="text-gray-600">
            Create and manage multi-agent workflows with conditional logic and orchestration
          </p>
        </div>
        <button
          onClick={() => navigate('/workflows/new')}
          className="btn-primary"
        >
          + Create Workflow
        </button>
      </div>

      {/* Error Message */}
      {error && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded">
          {error}
        </div>
      )}

      {/* Filter Tabs */}
      <div className="border-b border-gray-200">
        <nav className="-mb-px flex space-x-8">
          <button
            onClick={() => setFilter('all')}
            className={`py-4 px-1 border-b-2 font-medium text-sm ${
              filter === 'all'
                ? 'border-primary-500 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            All Workflows ({workflows.length})
          </button>
          <button
            onClick={() => setFilter('active')}
            className={`py-4 px-1 border-b-2 font-medium text-sm ${
              filter === 'active'
                ? 'border-primary-500 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            Active ({workflows.filter(w => w.isActive).length})
          </button>
          <button
            onClick={() => setFilter('inactive')}
            className={`py-4 px-1 border-b-2 font-medium text-sm ${
              filter === 'inactive'
                ? 'border-primary-500 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            Inactive ({workflows.filter(w => !w.isActive).length})
          </button>
        </nav>
      </div>

      {/* Workflows List */}
      {filteredWorkflows.length === 0 ? (
        <div className="card text-center py-12">
          <div className="text-gray-400 text-5xl mb-4">🔄</div>
          <h3 className="text-lg font-medium text-gray-900 mb-2">No workflows found</h3>
          <p className="text-gray-600 mb-6">
            Get started by creating your first workflow
          </p>
          <button
            onClick={() => navigate('/workflows/new')}
            className="btn-primary"
          >
            Create Workflow
          </button>
        </div>
      ) : (
        <div className="grid gap-6">
          {filteredWorkflows.map((workflow) => (
            <div key={workflow.id} className="card hover:shadow-lg transition-shadow">
              <div className="flex justify-between items-start mb-4">
                <div className="flex-1">
                  <div className="flex items-center gap-3 mb-2">
                    <h3 className="text-xl font-semibold text-gray-900">
                      {workflow.name}
                    </h3>
                    <span
                      className={`px-2 py-1 text-xs font-semibold rounded-full ${
                        workflow.isActive
                          ? 'bg-green-100 text-green-800'
                          : 'bg-gray-100 text-gray-800'
                      }`}
                    >
                      {workflow.isActive ? 'Active' : 'Inactive'}
                    </span>
                    <span className="px-2 py-1 text-xs font-semibold rounded-full bg-blue-100 text-blue-800">
                      {workflow.triggerType}
                    </span>
                    <span className="px-2 py-1 text-xs font-semibold rounded-full bg-purple-100 text-purple-800">
                      {workflow.executionMode}
                    </span>
                  </div>
                  {workflow.description && (
                    <p className="text-gray-600 text-sm">{workflow.description}</p>
                  )}
                </div>
              </div>

              {/* Workflow Stats */}
              <div className="flex items-center gap-6 text-sm text-gray-500 mb-4">
                <div>
                  <span className="font-medium text-gray-700">Steps:</span> {workflow.stepCount || 0}
                </div>
                <div>
                  <span className="font-medium text-gray-700">Created:</span>{' '}
                  {new Date(workflow.createdAt).toLocaleDateString()}
                </div>
                {workflow.lastExecutedAt && (
                  <div>
                    <span className="font-medium text-gray-700">Last Run:</span>{' '}
                    {new Date(workflow.lastExecutedAt).toLocaleString()}
                  </div>
                )}
              </div>

              {/* Action Buttons */}
              <div className="flex gap-2 pt-4 border-t">
                <button
                  onClick={() => navigate(`/workflows/${workflow.id}`)}
                  className="px-3 py-2 text-sm bg-primary-50 text-primary-700 rounded hover:bg-primary-100"
                >
                  Edit
                </button>
                <button
                  onClick={() => handleExecuteWorkflow(workflow.id, workflow.name)}
                  className="px-3 py-2 text-sm bg-green-50 text-green-700 rounded hover:bg-green-100"
                >
                  Execute
                </button>
                {workflow.isActive ? (
                  <button
                    onClick={() => handleDeactivateWorkflow(workflow.id)}
                    className="px-3 py-2 text-sm bg-yellow-50 text-yellow-700 rounded hover:bg-yellow-100"
                  >
                    Deactivate
                  </button>
                ) : (
                  <button
                    onClick={() => handleActivateWorkflow(workflow.id)}
                    className="px-3 py-2 text-sm bg-green-50 text-green-700 rounded hover:bg-green-100"
                  >
                    Activate
                  </button>
                )}
                <button
                  onClick={() => handleDeleteWorkflow(workflow.id, workflow.name)}
                  className="px-3 py-2 text-sm bg-red-50 text-red-700 rounded hover:bg-red-100 ml-auto"
                >
                  Delete
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default WorkflowManagement
