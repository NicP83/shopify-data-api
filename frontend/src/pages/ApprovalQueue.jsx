import { useState, useEffect } from 'react'
import api from '../services/api'

function ApprovalQueue() {
  const [approvals, setApprovals] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [selectedApproval, setSelectedApproval] = useState(null)
  const [actionInProgress, setActionInProgress] = useState(false)
  const [approverName, setApproverName] = useState('admin@example.com')
  const [comments, setComments] = useState('')

  useEffect(() => {
    loadApprovals()
    // Auto-refresh every 10 seconds
    const interval = setInterval(loadApprovals, 10000)
    return () => clearInterval(interval)
  }, [])

  const loadApprovals = async () => {
    try {
      setLoading(true)
      const response = await api.getPendingApprovals()
      setApprovals(response.data)
    } catch (error) {
      console.error('Error loading approvals:', error)
      setError('Failed to load approval requests')
    } finally {
      setLoading(false)
    }
  }

  const handleApprove = async (approval) => {
    if (!window.confirm(`Approve request for workflow "${approval.workflowExecution?.workflow?.name}"?`)) {
      return
    }

    try {
      setActionInProgress(true)
      await api.approveRequest(approval.id, approverName, comments)
      alert('Approval granted successfully')
      setComments('')
      setSelectedApproval(null)
      await loadApprovals()
    } catch (error) {
      console.error('Error approving request:', error)
      alert('Failed to approve request: ' + (error.response?.data?.message || error.message))
    } finally {
      setActionInProgress(false)
    }
  }

  const handleReject = async (approval) => {
    const reason = window.prompt('Please provide a reason for rejection:')
    if (!reason) {
      return
    }

    try {
      setActionInProgress(true)
      await api.rejectRequest(approval.id, approverName, reason)
      alert('Request rejected')
      setComments('')
      setSelectedApproval(null)
      await loadApprovals()
    } catch (error) {
      console.error('Error rejecting request:', error)
      alert('Failed to reject request: ' + (error.response?.data?.message || error.message))
    } finally {
      setActionInProgress(false)
    }
  }

  const formatDate = (dateStr) => {
    if (!dateStr) return 'N/A'
    const date = new Date(dateStr)
    return date.toLocaleString()
  }

  if (loading && approvals.length === 0) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-gray-500">Loading approvals...</div>
      </div>
    )
  }

  return (
    <div className="max-w-7xl mx-auto">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-gray-900">Approval Queue</h1>
        <p className="text-gray-600 mt-2">
          Pending approval requests for workflow executions
        </p>
      </div>

      {/* Error Message */}
      {error && (
        <div className="mb-4 bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded">
          {error}
        </div>
      )}

      {/* Approver Name Input */}
      <div className="mb-6 bg-white rounded-lg shadow-sm border p-4">
        <label className="block text-sm font-medium text-gray-700 mb-2">
          Your Name/Email (for audit trail)
        </label>
        <input
          type="text"
          value={approverName}
          onChange={(e) => setApproverName(e.target.value)}
          className="w-full max-w-md px-3 py-2 border border-gray-300 rounded-lg"
          placeholder="e.g., john@example.com"
        />
      </div>

      {/* Approvals List */}
      {approvals.length === 0 ? (
        <div className="bg-white rounded-lg shadow-sm border p-12 text-center">
          <div className="text-6xl mb-4">✅</div>
          <h3 className="text-lg font-semibold text-gray-900 mb-2">No Pending Approvals</h3>
          <p className="text-gray-600">
            All workflow approvals have been processed. New requests will appear here.
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {approvals.map((approval) => (
            <div
              key={approval.id}
              className="bg-white rounded-lg shadow-sm border p-6 hover:shadow-md transition"
            >
              <div className="flex justify-between items-start">
                <div className="flex-1">
                  {/* Header */}
                  <div className="flex items-center gap-3 mb-3">
                    <span className="px-3 py-1 bg-yellow-100 text-yellow-800 text-sm font-medium rounded-full">
                      PENDING
                    </span>
                    {approval.requiredRole && (
                      <span className="px-3 py-1 bg-blue-100 text-blue-800 text-xs font-medium rounded-full">
                        Role: {approval.requiredRole}
                      </span>
                    )}
                  </div>

                  {/* Workflow Info */}
                  <h3 className="text-xl font-semibold text-gray-900 mb-2">
                    {approval.workflowExecution?.workflow?.name || 'Workflow Approval'}
                  </h3>

                  <div className="grid grid-cols-2 gap-4 mb-4 text-sm">
                    <div>
                      <span className="text-gray-600">Execution ID:</span>
                      <span className="ml-2 font-mono text-gray-900">
                        #{approval.workflowExecution?.id}
                      </span>
                    </div>
                    <div>
                      <span className="text-gray-600">Step:</span>
                      <span className="ml-2 text-gray-900">
                        {approval.workflowStep?.name || 'N/A'}
                      </span>
                    </div>
                    <div>
                      <span className="text-gray-600">Requested:</span>
                      <span className="ml-2 text-gray-900">
                        {formatDate(approval.requestedAt)}
                      </span>
                    </div>
                    {approval.timeoutAt && (
                      <div>
                        <span className="text-gray-600">Timeout:</span>
                        <span className="ml-2 text-gray-900">
                          {formatDate(approval.timeoutAt)}
                        </span>
                      </div>
                    )}
                  </div>

                  {/* Workflow Context */}
                  {approval.workflowExecution?.triggerDataJson && (
                    <details className="mb-4">
                      <summary className="cursor-pointer text-sm font-medium text-primary-600 hover:text-primary-700">
                        View Workflow Context
                      </summary>
                      <pre className="mt-2 p-3 bg-gray-50 rounded text-xs overflow-auto max-h-40">
                        {JSON.stringify(approval.workflowExecution.triggerDataJson, null, 2)}
                      </pre>
                    </details>
                  )}

                  {/* Comments Input */}
                  {selectedApproval?.id === approval.id && (
                    <div className="mb-4">
                      <label className="block text-sm font-medium text-gray-700 mb-2">
                        Comments (Optional)
                      </label>
                      <textarea
                        value={comments}
                        onChange={(e) => setComments(e.target.value)}
                        rows="3"
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                        placeholder="Add any comments about this approval..."
                      />
                    </div>
                  )}
                </div>
              </div>

              {/* Actions */}
              <div className="flex gap-3 mt-4 pt-4 border-t">
                {selectedApproval?.id === approval.id ? (
                  <>
                    <button
                      onClick={() => handleApprove(approval)}
                      disabled={actionInProgress}
                      className="px-6 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50 transition"
                    >
                      {actionInProgress ? 'Processing...' : 'Confirm Approval'}
                    </button>
                    <button
                      onClick={() => setSelectedApproval(null)}
                      disabled={actionInProgress}
                      className="px-6 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 transition"
                    >
                      Cancel
                    </button>
                  </>
                ) : (
                  <>
                    <button
                      onClick={() => setSelectedApproval(approval)}
                      className="px-6 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition"
                    >
                      Approve
                    </button>
                    <button
                      onClick={() => handleReject(approval)}
                      disabled={actionInProgress}
                      className="px-6 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50 transition"
                    >
                      Reject
                    </button>
                  </>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default ApprovalQueue
