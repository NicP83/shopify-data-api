import { useState, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import api from '../services/api'

function WorkflowExecutions() {
  const { workflowId } = useParams()
  const navigate = useNavigate()

  const [executions, setExecutions] = useState([])
  const [workflows, setWorkflows] = useState([])
  const [selectedExecution, setSelectedExecution] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [filter, setFilter] = useState({
    status: 'all',
    workflowId: workflowId || 'all'
  })

  useEffect(() => {
    loadWorkflows()
    loadExecutions()
  }, [filter])

  const loadWorkflows = async () => {
    try {
      const response = await api.getWorkflows()
      setWorkflows(response.data)
    } catch (error) {
      console.error('Error loading workflows:', error)
    }
  }

  const loadExecutions = async () => {
    try {
      setLoading(true)
      setError(null)

      // Get executions for specific workflow or all
      const response = filter.workflowId !== 'all'
        ? await api.getWorkflowExecutions(filter.workflowId)
        : await api.getAllExecutions()

      let executionList = response.data || []

      // Filter by status
      if (filter.status !== 'all') {
        executionList = executionList.filter(ex => ex.status === filter.status)
      }

      setExecutions(executionList)
    } catch (error) {
      console.error('Error loading executions:', error)
      setError('Failed to load executions')
    } finally {
      setLoading(false)
    }
  }

  const getStatusColor = (status) => {
    switch (status) {
      case 'COMPLETED':
        return 'bg-green-100 text-green-800'
      case 'RUNNING':
        return 'bg-blue-100 text-blue-800'
      case 'FAILED':
        return 'bg-red-100 text-red-800'
      case 'PAUSED':
        return 'bg-yellow-100 text-yellow-800'
      default:
        return 'bg-gray-100 text-gray-800'
    }
  }

  const getStatusIcon = (status) => {
    switch (status) {
      case 'COMPLETED':
        return '✓'
      case 'RUNNING':
        return '⟳'
      case 'FAILED':
        return '✗'
      case 'PAUSED':
        return '⏸'
      default:
        return '•'
    }
  }

  const formatDuration = (startedAt, completedAt) => {
    if (!completedAt) return 'Running...'
    const start = new Date(startedAt)
    const end = new Date(completedAt)
    const durationMs = end - start
    const seconds = Math.floor(durationMs / 1000)
    if (seconds < 60) return `${seconds}s`
    const minutes = Math.floor(seconds / 60)
    const remainingSeconds = seconds % 60
    return `${minutes}m ${remainingSeconds}s`
  }

  const handleViewDetails = (execution) => {
    setSelectedExecution(execution)
  }

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-gray-500">Loading executions...</div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-start">
        <div>
          <h1 className="text-3xl font-bold text-gray-900 mb-2">
            Workflow Executions
          </h1>
          <p className="text-gray-600">
            Monitor and track workflow execution history
          </p>
        </div>
        <button
          onClick={() => navigate('/workflows')}
          className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
        >
          Back to Workflows
        </button>
      </div>

      {/* Error Message */}
      {error && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded">
          {error}
        </div>
      )}

      {/* Filters */}
      <div className="card">
        <div className="flex gap-4">
          <div className="flex-1">
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Workflow
            </label>
            <select
              value={filter.workflowId}
              onChange={(e) => setFilter({ ...filter, workflowId: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            >
              <option value="all">All Workflows</option>
              {workflows.map((wf) => (
                <option key={wf.id} value={wf.id}>
                  {wf.name}
                </option>
              ))}
            </select>
          </div>
          <div className="flex-1">
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Status
            </label>
            <select
              value={filter.status}
              onChange={(e) => setFilter({ ...filter, status: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            >
              <option value="all">All Statuses</option>
              <option value="RUNNING">Running</option>
              <option value="COMPLETED">Completed</option>
              <option value="FAILED">Failed</option>
              <option value="PAUSED">Paused</option>
            </select>
          </div>
        </div>
      </div>

      {/* Executions List */}
      {executions.length === 0 ? (
        <div className="card text-center py-12">
          <div className="text-gray-400 text-5xl mb-4">📊</div>
          <h3 className="text-lg font-medium text-gray-900 mb-2">No executions found</h3>
          <p className="text-gray-600 mb-6">
            No workflow executions match your filters
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {executions.map((execution) => (
            <div
              key={execution.id}
              className="card hover:shadow-lg transition-shadow cursor-pointer"
              onClick={() => handleViewDetails(execution)}
            >
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <div className="flex items-center gap-3 mb-2">
                    <span className={`px-3 py-1 rounded-full text-sm font-semibold ${getStatusColor(execution.status)}`}>
                      {getStatusIcon(execution.status)} {execution.status}
                    </span>
                    <h3 className="text-lg font-semibold text-gray-900">
                      {execution.workflowName || `Workflow #${execution.workflowId}`}
                    </h3>
                  </div>

                  <div className="grid grid-cols-3 gap-4 text-sm text-gray-600">
                    <div>
                      <span className="font-medium">Started:</span>{' '}
                      {new Date(execution.startedAt).toLocaleString()}
                    </div>
                    {execution.completedAt && (
                      <div>
                        <span className="font-medium">Duration:</span>{' '}
                        {formatDuration(execution.startedAt, execution.completedAt)}
                      </div>
                    )}
                    <div>
                      <span className="font-medium">Execution ID:</span> {execution.id}
                    </div>
                  </div>

                  {execution.errorMessage && (
                    <div className="mt-2 text-sm text-red-600">
                      <span className="font-medium">Error:</span> {execution.errorMessage}
                    </div>
                  )}
                </div>

                <button
                  onClick={(e) => {
                    e.stopPropagation()
                    handleViewDetails(execution)
                  }}
                  className="px-4 py-2 bg-primary-50 text-primary-700 rounded-lg hover:bg-primary-100"
                >
                  View Details
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Execution Detail Modal */}
      {selectedExecution && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg max-w-4xl w-full max-h-[90vh] overflow-y-auto p-6">
            <div className="flex justify-between items-start mb-6">
              <div>
                <h2 className="text-2xl font-bold mb-2">
                  Execution Details
                </h2>
                <div className="flex items-center gap-2">
                  <span className={`px-3 py-1 rounded-full text-sm font-semibold ${getStatusColor(selectedExecution.status)}`}>
                    {getStatusIcon(selectedExecution.status)} {selectedExecution.status}
                  </span>
                  <span className="text-gray-600">ID: {selectedExecution.id}</span>
                </div>
              </div>
              <button
                onClick={() => setSelectedExecution(null)}
                className="text-gray-500 hover:text-gray-700 text-2xl"
              >
                ×
              </button>
            </div>

            <div className="space-y-6">
              {/* Execution Info */}
              <div className="card bg-gray-50">
                <h3 className="font-semibold mb-3">Execution Information</h3>
                <div className="grid grid-cols-2 gap-4 text-sm">
                  <div>
                    <span className="font-medium">Workflow:</span> {selectedExecution.workflowName}
                  </div>
                  <div>
                    <span className="font-medium">Status:</span> {selectedExecution.status}
                  </div>
                  <div>
                    <span className="font-medium">Started:</span>{' '}
                    {new Date(selectedExecution.startedAt).toLocaleString()}
                  </div>
                  {selectedExecution.completedAt && (
                    <div>
                      <span className="font-medium">Completed:</span>{' '}
                      {new Date(selectedExecution.completedAt).toLocaleString()}
                    </div>
                  )}
                  {selectedExecution.completedAt && (
                    <div>
                      <span className="font-medium">Duration:</span>{' '}
                      {formatDuration(selectedExecution.startedAt, selectedExecution.completedAt)}
                    </div>
                  )}
                </div>
              </div>

              {/* Trigger Data */}
              {selectedExecution.triggerDataJson && (
                <div>
                  <h3 className="font-semibold mb-3">Trigger Data</h3>
                  <pre className="bg-gray-100 p-4 rounded-lg text-sm overflow-x-auto">
                    {JSON.stringify(selectedExecution.triggerDataJson, null, 2)}
                  </pre>
                </div>
              )}

              {/* Context Data */}
              {selectedExecution.contextDataJson && (
                <div>
                  <h3 className="font-semibold mb-3">Final Context</h3>
                  <pre className="bg-gray-100 p-4 rounded-lg text-sm overflow-x-auto">
                    {JSON.stringify(selectedExecution.contextDataJson, null, 2)}
                  </pre>
                </div>
              )}

              {/* Error Message */}
              {selectedExecution.errorMessage && (
                <div>
                  <h3 className="font-semibold mb-3 text-red-700">Error Details</h3>
                  <div className="bg-red-50 border border-red-200 p-4 rounded-lg text-sm text-red-800">
                    {selectedExecution.errorMessage}
                  </div>
                </div>
              )}
            </div>

            <div className="flex justify-end mt-6 pt-4 border-t">
              <button
                onClick={() => setSelectedExecution(null)}
                className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default WorkflowExecutions
