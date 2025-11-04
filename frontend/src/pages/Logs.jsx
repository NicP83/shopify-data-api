import { useState, useEffect } from 'react'
import api from '../services/api'

function Logs() {
  const [logs, setLogs] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalItems, setTotalItems] = useState(0)
  const [expandedRow, setExpandedRow] = useState(null)
  const [autoRefresh, setAutoRefresh] = useState(true)

  const pageSize = 50

  useEffect(() => {
    loadLogs()
  }, [page])

  useEffect(() => {
    if (!autoRefresh) return

    // Auto-refresh every 10 seconds
    const interval = setInterval(() => {
      loadLogs(true) // Silent refresh (no loading state)
    }, 10000)

    return () => clearInterval(interval)
  }, [autoRefresh, page])

  const loadLogs = async (silent = false) => {
    if (!silent) {
      setLoading(true)
    }
    setError(null)

    try {
      const response = await fetch(
        `/api/logs/chat?shop=hearnshobbies.myshopify.com&page=${page}&size=${pageSize}`
      )
      const data = await response.json()

      if (data.success) {
        setLogs(data.data.logs)
        setTotalPages(data.data.totalPages)
        setTotalItems(data.data.totalItems)
      } else {
        setError(data.message || 'Failed to load logs')
      }
    } catch (err) {
      console.error('Error loading logs:', err)
      setError('Failed to load logs: ' + err.message)
    } finally {
      if (!silent) {
        setLoading(false)
      }
    }
  }

  const toggleRow = (id) => {
    setExpandedRow(expandedRow === id ? null : id)
  }

  const truncateText = (text, maxLength = 100) => {
    if (!text) return '-'
    if (text.length <= maxLength) return text
    return text.substring(0, maxLength) + '...'
  }

  const formatDateTime = (dateString) => {
    if (!dateString) return '-'
    const date = new Date(dateString)
    return date.toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
  }

  const formatCost = (cost) => {
    if (!cost) return '$0.00'
    return `$${parseFloat(cost).toFixed(4)}`
  }

  const formatResponseTime = (ms) => {
    if (!ms) return '-'
    if (ms < 1000) return `${ms}ms`
    return `${(ms / 1000).toFixed(2)}s`
  }

  if (loading) {
    return (
      <div className="flex justify-center items-center py-20">
        <div className="text-gray-500">Loading chat logs...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded">
        {error}
      </div>
    )
  }

  return (
    <div className="max-w-full">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-800">Chat Interaction Logs</h1>
          <p className="text-gray-600 mt-2">
            Showing {logs.length} of {totalItems} total interactions
          </p>
        </div>
        <div className="flex items-center gap-4">
          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={autoRefresh}
              onChange={(e) => setAutoRefresh(e.target.checked)}
              className="rounded"
            />
            Auto-refresh (10s)
          </label>
          <button
            onClick={() => loadLogs()}
            className="px-4 py-2 bg-primary-600 text-white rounded hover:bg-primary-700 transition-colors"
          >
            Refresh
          </button>
        </div>
      </div>

      {logs.length === 0 ? (
        <div className="text-center py-20 text-gray-500">
          No chat interactions recorded yet
        </div>
      ) : (
        <>
          <div className="bg-white rounded-lg shadow overflow-hidden">
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Timestamp
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      User Message
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      AI Response
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Model
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Tools Used
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Response Time
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Cost
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      Status
                    </th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {logs.map((log) => (
                    <>
                      <tr
                        key={log.id}
                        onClick={() => toggleRow(log.id)}
                        className="hover:bg-gray-50 cursor-pointer transition-colors"
                      >
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {formatDateTime(log.createdAt)}
                        </td>
                        <td className="px-6 py-4 text-sm text-gray-700 max-w-xs">
                          {truncateText(log.userMessage)}
                        </td>
                        <td className="px-6 py-4 text-sm text-gray-700 max-w-xs">
                          {truncateText(log.assistantResponse)}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                          {log.modelUsed || '-'}
                        </td>
                        <td className="px-6 py-4 text-sm text-gray-600">
                          {log.toolsCalled && log.toolsCalled.length > 0 ? (
                            <span className="px-2 py-1 bg-blue-100 text-blue-800 rounded text-xs">
                              {log.toolsCalled.length} tool{log.toolsCalled.length > 1 ? 's' : ''}
                            </span>
                          ) : (
                            '-'
                          )}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                          {formatResponseTime(log.responseTimeMs)}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                          {formatCost(log.apiCostUsd)}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          {log.wasSuccessful ? (
                            <span className="px-2 py-1 bg-green-100 text-green-800 rounded text-xs">
                              Success
                            </span>
                          ) : (
                            <span className="px-2 py-1 bg-red-100 text-red-800 rounded text-xs">
                              Error
                            </span>
                          )}
                        </td>
                      </tr>
                      {expandedRow === log.id && (
                        <tr>
                          <td colSpan="8" className="px-6 py-4 bg-gray-50">
                            <div className="space-y-4">
                              <div>
                                <h4 className="font-semibold text-gray-700 mb-2">User Message:</h4>
                                <div className="bg-white p-3 rounded border border-gray-200 text-sm whitespace-pre-wrap">
                                  {log.userMessage}
                                </div>
                              </div>
                              <div>
                                <h4 className="font-semibold text-gray-700 mb-2">AI Response:</h4>
                                <div className="bg-white p-3 rounded border border-gray-200 text-sm whitespace-pre-wrap">
                                  {log.assistantResponse}
                                </div>
                              </div>
                              {log.toolsCalled && log.toolsCalled.length > 0 && (
                                <div>
                                  <h4 className="font-semibold text-gray-700 mb-2">Tools Called:</h4>
                                  <div className="flex flex-wrap gap-2">
                                    {log.toolsCalled.map((tool, idx) => (
                                      <span
                                        key={idx}
                                        className="px-3 py-1 bg-blue-100 text-blue-800 rounded text-sm"
                                      >
                                        {tool}
                                      </span>
                                    ))}
                                  </div>
                                </div>
                              )}
                              <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                                <div>
                                  <h4 className="font-semibold text-gray-700 text-sm">Session ID:</h4>
                                  <p className="text-sm text-gray-600">{log.sessionId || '-'}</p>
                                </div>
                                <div>
                                  <h4 className="font-semibold text-gray-700 text-sm">Tokens Used:</h4>
                                  <p className="text-sm text-gray-600">{log.tokensUsed || '-'}</p>
                                </div>
                                <div>
                                  <h4 className="font-semibold text-gray-700 text-sm">User Feedback:</h4>
                                  <p className="text-sm text-gray-600">{log.userFeedback || '-'}</p>
                                </div>
                                {!log.wasSuccessful && log.errorMessage && (
                                  <div className="col-span-2 md:col-span-4">
                                    <h4 className="font-semibold text-red-700 text-sm">Error Message:</h4>
                                    <p className="text-sm text-red-600">{log.errorMessage}</p>
                                  </div>
                                )}
                              </div>
                            </div>
                          </td>
                        </tr>
                      )}
                    </>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="mt-6 flex items-center justify-between">
              <div className="text-sm text-gray-700">
                Page {page + 1} of {totalPages}
              </div>
              <div className="flex gap-2">
                <button
                  onClick={() => setPage(Math.max(0, page - 1))}
                  disabled={page === 0}
                  className="px-4 py-2 bg-white border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  Previous
                </button>
                <button
                  onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
                  disabled={page >= totalPages - 1}
                  className="px-4 py-2 bg-white border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  Next
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}

export default Logs
