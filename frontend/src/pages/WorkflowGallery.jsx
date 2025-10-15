import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'

/**
 * WorkflowGallery - Browse and execute public workflows
 *
 * Displays all active public workflows in a gallery format
 * Users can browse workflows and launch them in their preferred interface
 */
function WorkflowGallery() {
  const navigate = useNavigate()

  const [workflows, setWorkflows] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [filter, setFilter] = useState('all') // all, form, chat

  useEffect(() => {
    loadPublicWorkflows()
  }, [])

  const loadPublicWorkflows = async () => {
    try {
      setLoading(true)
      // Get all active workflows
      const response = await api.getWorkflows(true)

      // Filter for public workflows only
      const publicWorkflows = response.data.filter(w => w.isPublic)
      setWorkflows(publicWorkflows)
    } catch (error) {
      console.error('Error loading workflows:', error)
      setError('Failed to load workflows')
    } finally {
      setLoading(false)
    }
  }

  const getFilteredWorkflows = () => {
    if (filter === 'all') return workflows
    return workflows.filter(w => w.interfaceType.toLowerCase() === filter)
  }

  const getInterfaceIcon = (interfaceType) => {
    switch (interfaceType) {
      case 'FORM':
        return (
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
        )
      case 'CHAT':
        return (
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
          </svg>
        )
      case 'API':
        return (
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
          </svg>
        )
      default:
        return (
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4" />
          </svg>
        )
    }
  }

  const handleLaunchWorkflow = (workflow) => {
    if (workflow.interfaceType === 'CHAT') {
      navigate(`/workflow/chat/${workflow.id}`)
    } else {
      navigate(`/workflow/execute/${workflow.id}`)
    }
  }

  const filteredWorkflows = getFilteredWorkflows()

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto mb-4"></div>
          <p className="text-gray-600">Loading workflows...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-gray-900 mb-2">Workflow Gallery</h1>
        <p className="text-gray-600">
          Browse and execute public workflows. These workflows are available for anyone to use.
        </p>
      </div>

      {/* Filters */}
      <div className="card">
        <div className="flex gap-2">
          <button
            onClick={() => setFilter('all')}
            className={`px-4 py-2 rounded-lg font-medium transition-colors ${
              filter === 'all'
                ? 'bg-primary-600 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
          >
            All Workflows ({workflows.length})
          </button>
          <button
            onClick={() => setFilter('form')}
            className={`px-4 py-2 rounded-lg font-medium transition-colors ${
              filter === 'form'
                ? 'bg-primary-600 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
          >
            Form ({workflows.filter(w => w.interfaceType === 'FORM').length})
          </button>
          <button
            onClick={() => setFilter('chat')}
            className={`px-4 py-2 rounded-lg font-medium transition-colors ${
              filter === 'chat'
                ? 'bg-primary-600 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
          >
            Chat ({workflows.filter(w => w.interfaceType === 'CHAT').length})
          </button>
        </div>
      </div>

      {/* Error Message */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4">
          <p className="text-red-700">{error}</p>
        </div>
      )}

      {/* Workflow Grid */}
      {filteredWorkflows.length === 0 ? (
        <div className="card text-center py-12">
          <svg className="w-16 h-16 mx-auto text-gray-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" />
          </svg>
          <h3 className="text-lg font-semibold text-gray-900 mb-2">No Workflows Found</h3>
          <p className="text-gray-600">
            {filter === 'all'
              ? 'There are no public workflows available yet.'
              : `There are no ${filter} workflows available yet.`
            }
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {filteredWorkflows.map((workflow) => (
            <div
              key={workflow.id}
              className="card hover:shadow-lg transition-shadow cursor-pointer"
              onClick={() => handleLaunchWorkflow(workflow)}
            >
              {/* Header */}
              <div className="flex items-start justify-between mb-4">
                <div className="flex items-center gap-3">
                  <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${
                    workflow.interfaceType === 'CHAT'
                      ? 'bg-purple-100 text-purple-600'
                      : 'bg-blue-100 text-blue-600'
                  }`}>
                    {getInterfaceIcon(workflow.interfaceType)}
                  </div>
                  <div>
                    <h3 className="font-semibold text-gray-900">{workflow.name}</h3>
                    <p className="text-xs text-gray-500">{workflow.interfaceType} Interface</p>
                  </div>
                </div>
              </div>

              {/* Description */}
              <p className="text-sm text-gray-600 mb-4 line-clamp-3">
                {workflow.description || 'No description provided'}
              </p>

              {/* Metadata */}
              <div className="flex items-center justify-between text-xs text-gray-500 mb-4">
                <span className="flex items-center gap-1">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                  </svg>
                  {workflow.stepCount || 0} steps
                </span>
                <span className="flex items-center gap-1">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  {workflow.executionMode}
                </span>
              </div>

              {/* Action Button */}
              <button
                className="w-full btn-primary text-sm"
                onClick={(e) => {
                  e.stopPropagation()
                  handleLaunchWorkflow(workflow)
                }}
              >
                Launch Workflow
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Info Box */}
      {workflows.length > 0 && (
        <div className="card bg-blue-50 border-blue-200">
          <div className="flex gap-3">
            <svg className="w-5 h-5 text-blue-600 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <div>
              <h4 className="font-semibold text-blue-900 mb-1">About Public Workflows</h4>
              <p className="text-sm text-blue-800">
                Public workflows can be executed by anyone without authentication.
                They are designed to be shared and used across your organization.
                Choose between Form interfaces for structured data entry or Chat interfaces for conversational execution.
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default WorkflowGallery
