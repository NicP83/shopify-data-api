import { useState, useEffect } from 'react'
import {
  LineChart,
  Line,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts'

const COLORS = ['#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899']

function ChatAnalytics() {
  const [summary, setSummary] = useState(null)
  const [dailyStats, setDailyStats] = useState([])
  const [recentInteractions, setRecentInteractions] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [days, setDays] = useState(30)
  const [selectedSession, setSelectedSession] = useState(null)
  const [sessionHistory, setSessionHistory] = useState([])

  const shop = 'hearnshobbies.myshopify.com'

  useEffect(() => {
    loadAnalytics()
  }, [days])

  const loadAnalytics = async () => {
    setLoading(true)
    setError(null)

    try {
      // Load summary
      const summaryRes = await fetch(`/api/analytics/chat/summary?shop=${shop}`)
      const summaryData = await summaryRes.json()

      if (summaryData.success) {
        setSummary(summaryData.data)
      }

      // Load daily stats
      const dailyRes = await fetch(`/api/analytics/chat/daily?shop=${shop}&days=${days}`)
      const dailyData = await dailyRes.json()

      if (dailyData.success) {
        setDailyStats(dailyData.data)
      }

      // Load recent interactions
      const recentRes = await fetch(`/api/analytics/chat/recent?shop=${shop}&limit=10`)
      const recentData = await recentRes.json()

      if (recentData.success) {
        setRecentInteractions(recentData.data)
      }
    } catch (err) {
      console.error('Error loading analytics:', err)
      setError('Failed to load analytics: ' + err.message)
    } finally {
      setLoading(false)
    }
  }

  const loadSessionHistory = async (sessionId) => {
    try {
      const res = await fetch(`/api/analytics/chat/session/${sessionId}`)
      const data = await res.json()

      if (data.success) {
        setSessionHistory(data.data)
        setSelectedSession(sessionId)
      }
    } catch (err) {
      console.error('Error loading session history:', err)
    }
  }

  const formatCost = (cost) => {
    if (!cost) return '$0.00'
    return `$${parseFloat(cost).toFixed(4)}`
  }

  const formatDateTime = (dateString) => {
    if (!dateString) return '-'
    const date = new Date(dateString)
    return date.toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  const truncateText = (text, maxLength = 80) => {
    if (!text) return '-'
    if (text.length <= maxLength) return text
    return text.substring(0, maxLength) + '...'
  }

  if (loading) {
    return (
      <div className="flex justify-center items-center py-20">
        <div className="text-gray-500">Loading analytics...</div>
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

  if (!summary) {
    return (
      <div className="text-center py-20 text-gray-500">
        No analytics data available
      </div>
    )
  }

  // Prepare model usage data for pie chart
  const modelUsageData = summary.modelUsage?.map((item) => ({
    name: item.model_used,
    value: parseInt(item.usage_count),
  })) || []

  return (
    <div className="max-w-full">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-800">Chat Analytics Dashboard</h1>
          <p className="text-gray-600 mt-2">Performance insights and conversation metrics</p>
        </div>
        <div className="flex items-center gap-2">
          <label className="text-sm text-gray-700">Time period:</label>
          <select
            value={days}
            onChange={(e) => setDays(parseInt(e.target.value))}
            className="px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
          >
            <option value={7}>Last 7 days</option>
            <option value={14}>Last 14 days</option>
            <option value={30}>Last 30 days</option>
            <option value={60}>Last 60 days</option>
            <option value={90}>Last 90 days</option>
          </select>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4 mb-8">
        <div className="bg-white rounded-lg shadow p-6">
          <div className="text-sm text-gray-500 mb-1">Total Conversations</div>
          <div className="text-3xl font-bold text-gray-900">
            {summary.totalInteractions || 0}
          </div>
        </div>

        <div className="bg-white rounded-lg shadow p-6">
          <div className="text-sm text-gray-500 mb-1">Success Rate</div>
          <div className="text-3xl font-bold text-green-600">
            {summary.successRate || '0%'}
          </div>
          <div className="text-xs text-gray-500 mt-1">
            {summary.successfulInteractions || 0} successful
          </div>
        </div>

        <div className="bg-white rounded-lg shadow p-6">
          <div className="text-sm text-gray-500 mb-1">Avg Response Time</div>
          <div className="text-3xl font-bold text-blue-600">
            {summary.averageResponseTimeMs
              ? `${(summary.averageResponseTimeMs / 1000).toFixed(2)}s`
              : '-'}
          </div>
        </div>

        <div className="bg-white rounded-lg shadow p-6">
          <div className="text-sm text-gray-500 mb-1">Total API Cost</div>
          <div className="text-3xl font-bold text-purple-600">
            {summary.totalApiCost || '$0.0000'}
          </div>
        </div>

        <div className="bg-white rounded-lg shadow p-6">
          <div className="text-sm text-gray-500 mb-1">Recent Activity</div>
          <div className="text-3xl font-bold text-orange-600">
            {summary.recentInteractionsCount || 0}
          </div>
          <div className="text-xs text-gray-500 mt-1">Last 10 interactions</div>
        </div>
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
        {/* Daily Conversation Volume */}
        <div className="bg-white rounded-lg shadow p-6">
          <h3 className="text-lg font-semibold text-gray-800 mb-4">
            Daily Conversation Volume
          </h3>
          {dailyStats.length > 0 ? (
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={dailyStats}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis
                  dataKey="date"
                  tick={{ fontSize: 12 }}
                  tickFormatter={(value) => {
                    const date = new Date(value)
                    return `${date.getMonth() + 1}/${date.getDate()}`
                  }}
                />
                <YAxis />
                <Tooltip
                  labelFormatter={(value) => {
                    const date = new Date(value)
                    return date.toLocaleDateString()
                  }}
                />
                <Legend />
                <Line
                  type="monotone"
                  dataKey="conversation_count"
                  stroke="#3B82F6"
                  strokeWidth={2}
                  name="Conversations"
                  dot={{ r: 4 }}
                  activeDot={{ r: 6 }}
                />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <div className="h-[300px] flex items-center justify-center text-gray-500">
              No data available
            </div>
          )}
        </div>

        {/* Response Time Trend */}
        <div className="bg-white rounded-lg shadow p-6">
          <h3 className="text-lg font-semibold text-gray-800 mb-4">
            Response Time Trend
          </h3>
          {dailyStats.length > 0 ? (
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={dailyStats}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis
                  dataKey="date"
                  tick={{ fontSize: 12 }}
                  tickFormatter={(value) => {
                    const date = new Date(value)
                    return `${date.getMonth() + 1}/${date.getDate()}`
                  }}
                />
                <YAxis label={{ value: 'ms', angle: -90, position: 'insideLeft' }} />
                <Tooltip
                  labelFormatter={(value) => {
                    const date = new Date(value)
                    return date.toLocaleDateString()
                  }}
                  formatter={(value) => [`${value}ms`, 'Avg Response Time']}
                />
                <Legend />
                <Bar
                  dataKey="avg_response_time"
                  fill="#10B981"
                  name="Avg Response Time"
                />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <div className="h-[300px] flex items-center justify-center text-gray-500">
              No data available
            </div>
          )}
        </div>
      </div>

      {/* Model Usage and Cost Row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
        {/* Model Usage Distribution */}
        <div className="bg-white rounded-lg shadow p-6">
          <h3 className="text-lg font-semibold text-gray-800 mb-4">
            Model Usage Distribution
          </h3>
          {modelUsageData.length > 0 ? (
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie
                  data={modelUsageData}
                  cx="50%"
                  cy="50%"
                  labelLine={false}
                  label={({ name, percent }) =>
                    `${name.replace('claude-', '')}: ${(percent * 100).toFixed(0)}%`
                  }
                  outerRadius={100}
                  fill="#8884d8"
                  dataKey="value"
                >
                  {modelUsageData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <div className="h-[300px] flex items-center justify-center text-gray-500">
              No data available
            </div>
          )}
        </div>

        {/* Daily API Cost */}
        <div className="bg-white rounded-lg shadow p-6">
          <h3 className="text-lg font-semibold text-gray-800 mb-4">
            Daily API Cost
          </h3>
          {dailyStats.length > 0 ? (
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={dailyStats}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis
                  dataKey="date"
                  tick={{ fontSize: 12 }}
                  tickFormatter={(value) => {
                    const date = new Date(value)
                    return `${date.getMonth() + 1}/${date.getDate()}`
                  }}
                />
                <YAxis
                  tickFormatter={(value) => `$${value.toFixed(4)}`}
                  label={{ value: 'USD', angle: -90, position: 'insideLeft' }}
                />
                <Tooltip
                  labelFormatter={(value) => {
                    const date = new Date(value)
                    return date.toLocaleDateString()
                  }}
                  formatter={(value) => [`$${parseFloat(value).toFixed(4)}`, 'Total Cost']}
                />
                <Legend />
                <Bar dataKey="total_cost" fill="#8B5CF6" name="Total Cost" />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <div className="h-[300px] flex items-center justify-center text-gray-500">
              No data available
            </div>
          )}
        </div>
      </div>

      {/* Recent Interactions Table */}
      <div className="bg-white rounded-lg shadow overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-200">
          <h3 className="text-lg font-semibold text-gray-800">Recent Conversations</h3>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Time
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  User Message
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Response (Preview)
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Response Time
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Cost
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Session
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {recentInteractions.map((interaction) => (
                <tr key={interaction.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {formatDateTime(interaction.createdAt)}
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-700 max-w-xs">
                    {truncateText(interaction.userMessage)}
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-700 max-w-xs">
                    {truncateText(interaction.assistantResponse)}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                    {interaction.responseTimeMs
                      ? `${(interaction.responseTimeMs / 1000).toFixed(2)}s`
                      : '-'}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                    {formatCost(interaction.apiCostUsd)}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm">
                    {interaction.sessionId ? (
                      <button
                        onClick={() => loadSessionHistory(interaction.sessionId)}
                        className="text-primary-600 hover:text-primary-800 underline"
                      >
                        View
                      </button>
                    ) : (
                      '-'
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Session Detail Modal */}
      {selectedSession && sessionHistory.length > 0 && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg shadow-xl max-w-4xl w-full max-h-[90vh] overflow-hidden flex flex-col">
            <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
              <h3 className="text-xl font-semibold text-gray-800">
                Session: {selectedSession}
              </h3>
              <button
                onClick={() => {
                  setSelectedSession(null)
                  setSessionHistory([])
                }}
                className="text-gray-400 hover:text-gray-600"
              >
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M6 18L18 6M6 6l12 12"
                  />
                </svg>
              </button>
            </div>
            <div className="p-6 overflow-y-auto flex-1">
              <div className="space-y-4">
                {sessionHistory.map((interaction, idx) => (
                  <div key={interaction.id} className="border-l-4 border-primary-500 pl-4">
                    <div className="text-xs text-gray-500 mb-2">
                      {formatDateTime(interaction.createdAt)} • {interaction.responseTimeMs}ms •{' '}
                      {formatCost(interaction.apiCostUsd)}
                    </div>
                    <div className="mb-3">
                      <div className="text-sm font-semibold text-gray-700 mb-1">User:</div>
                      <div className="bg-gray-100 p-3 rounded text-sm whitespace-pre-wrap">
                        {interaction.userMessage}
                      </div>
                    </div>
                    <div>
                      <div className="text-sm font-semibold text-gray-700 mb-1">Assistant:</div>
                      <div className="bg-primary-50 p-3 rounded text-sm whitespace-pre-wrap">
                        {interaction.assistantResponse}
                      </div>
                    </div>
                    {idx < sessionHistory.length - 1 && (
                      <div className="my-4 border-t border-gray-200"></div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default ChatAnalytics
