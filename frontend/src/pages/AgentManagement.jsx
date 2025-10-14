import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'

function AgentManagement() {
  const navigate = useNavigate()
  const [agents, setAgents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [filter, setFilter] = useState('all') // 'all', 'active', 'inactive'
  const [testingAgent, setTestingAgent] = useState(null)
  const [testInput, setTestInput] = useState('')
  const [testResult, setTestResult] = useState(null)

  useEffect(() => {
    loadAgents()
  }, [filter])

  const loadAgents = async () => {
    try {
      setLoading(true)
      setError(null)

      const activeOnly = filter === 'active' ? true : undefined
      const response = await api.getAgents(activeOnly)
      setAgents(response.data)
    } catch (error) {
      console.error('Error loading agents:', error)
      setError('Failed to load agents')
    } finally {
      setLoading(false)
    }
  }

  const handleActivateAgent = async (id) => {
    try {
      await api.activateAgent(id)
      loadAgents()
    } catch (error) {
      console.error('Error activating agent:', error)
      setError('Failed to activate agent')
    }
  }

  const handleDeactivateAgent = async (id) => {
    try {
      await api.deactivateAgent(id)
      loadAgents()
    } catch (error) {
      console.error('Error deactivating agent:', error)
      setError('Failed to deactivate agent')
    }
  }

  const handleDeleteAgent = async (id, name) => {
    if (!window.confirm(`Are you sure you want to delete agent "${name}"?`)) {
      return
    }

    try {
      await api.deleteAgent(id)
      loadAgents()
    } catch (error) {
      console.error('Error deleting agent:', error)
      setError('Failed to delete agent')
    }
  }

  const handleTestAgent = (agent) => {
    setTestingAgent(agent)
    setTestInput('')
    setTestResult(null)
  }

  const handleExecuteTest = async () => {
    if (!testInput.trim()) {
      alert('Please enter test input')
      return
    }

    try {
      const inputData = { message: testInput }
      const response = await api.executeAgent(testingAgent.id, inputData)
      setTestResult(response.data)
    } catch (error) {
      console.error('Error testing agent:', error)
      setTestResult({ error: error.response?.data?.message || error.message })
    }
  }

  const filteredAgents = agents.filter(agent => {
    if (filter === 'active') return agent.isActive
    if (filter === 'inactive') return !agent.isActive
    return true
  })

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-gray-500">Loading agents...</div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-start">
        <div>
          <h1 className="text-3xl font-bold text-gray-900 mb-2">Agent Management</h1>
          <p className="text-gray-600">
            Create and manage AI agents with custom system prompts and tools
          </p>
        </div>
        <button
          onClick={() => navigate('/agents/new')}
          className="btn-primary"
        >
          + Create Agent
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
            All Agents ({agents.length})
          </button>
          <button
            onClick={() => setFilter('active')}
            className={`py-4 px-1 border-b-2 font-medium text-sm ${
              filter === 'active'
                ? 'border-primary-500 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            Active ({agents.filter(a => a.isActive).length})
          </button>
          <button
            onClick={() => setFilter('inactive')}
            className={`py-4 px-1 border-b-2 font-medium text-sm ${
              filter === 'inactive'
                ? 'border-primary-500 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            Inactive ({agents.filter(a => !a.isActive).length})
          </button>
        </nav>
      </div>

      {/* Agents List */}
      {filteredAgents.length === 0 ? (
        <div className="card text-center py-12">
          <div className="text-gray-400 text-5xl mb-4">🤖</div>
          <h3 className="text-lg font-medium text-gray-900 mb-2">No agents found</h3>
          <p className="text-gray-600 mb-6">
            Get started by creating your first AI agent
          </p>
          <button
            onClick={() => navigate('/agents/new')}
            className="btn-primary"
          >
            Create Agent
          </button>
        </div>
      ) : (
        <div className="grid gap-6">
          {filteredAgents.map((agent) => (
            <div key={agent.id} className="card hover:shadow-lg transition-shadow">
              <div className="flex justify-between items-start mb-4">
                <div className="flex-1">
                  <div className="flex items-center gap-3 mb-2">
                    <h3 className="text-xl font-semibold text-gray-900">
                      {agent.name}
                    </h3>
                    <span
                      className={`px-2 py-1 text-xs font-semibold rounded-full ${
                        agent.isActive
                          ? 'bg-green-100 text-green-800'
                          : 'bg-gray-100 text-gray-800'
                      }`}
                    >
                      {agent.isActive ? 'Active' : 'Inactive'}
                    </span>
                    <span className="px-2 py-1 text-xs font-semibold rounded-full bg-blue-100 text-blue-800">
                      {agent.modelProvider}/{agent.modelName}
                    </span>
                  </div>
                  {agent.description && (
                    <p className="text-gray-600 text-sm mb-3">{agent.description}</p>
                  )}
                  {agent.systemPrompt && (
                    <div className="text-xs text-gray-500 mt-2">
                      <span className="font-medium">System Prompt:</span>{' '}
                      {agent.systemPrompt.substring(0, 150)}
                      {agent.systemPrompt.length > 150 ? '...' : ''}
                    </div>
                  )}
                </div>
              </div>

              {/* Agent Stats */}
              <div className="flex items-center gap-6 text-sm text-gray-500 mb-4">
                <div>
                  <span className="font-medium text-gray-700">Temperature:</span> {agent.temperature}
                </div>
                <div>
                  <span className="font-medium text-gray-700">Max Tokens:</span> {agent.maxTokens}
                </div>
                <div>
                  <span className="font-medium text-gray-700">Tools:</span> {agent.toolCount || 0}
                </div>
                <div>
                  <span className="font-medium text-gray-700">Created:</span>{' '}
                  {new Date(agent.createdAt).toLocaleDateString()}
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex gap-2 pt-4 border-t">
                <button
                  onClick={() => navigate(`/agents/${agent.id}`)}
                  className="px-3 py-2 text-sm bg-primary-50 text-primary-700 rounded hover:bg-primary-100"
                >
                  Edit
                </button>
                <button
                  onClick={() => handleTestAgent(agent)}
                  className="px-3 py-2 text-sm bg-green-50 text-green-700 rounded hover:bg-green-100"
                >
                  Test
                </button>
                {agent.isActive ? (
                  <button
                    onClick={() => handleDeactivateAgent(agent.id)}
                    className="px-3 py-2 text-sm bg-yellow-50 text-yellow-700 rounded hover:bg-yellow-100"
                  >
                    Deactivate
                  </button>
                ) : (
                  <button
                    onClick={() => handleActivateAgent(agent.id)}
                    className="px-3 py-2 text-sm bg-green-50 text-green-700 rounded hover:bg-green-100"
                  >
                    Activate
                  </button>
                )}
                <button
                  onClick={() => handleDeleteAgent(agent.id, agent.name)}
                  className="px-3 py-2 text-sm bg-red-50 text-red-700 rounded hover:bg-red-100 ml-auto"
                >
                  Delete
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Test Agent Modal */}
      {testingAgent && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg max-w-2xl w-full max-h-[90vh] overflow-y-auto p-6">
            <h2 className="text-2xl font-bold mb-4">
              Test Agent: {testingAgent.name}
            </h2>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Test Input
                </label>
                <textarea
                  value={testInput}
                  onChange={(e) => setTestInput(e.target.value)}
                  rows="4"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  placeholder="Enter a message to test the agent..."
                />
              </div>

              {testResult && (
                <div className="border-t pt-4">
                  <h3 className="font-semibold mb-2">Result:</h3>
                  {testResult.error ? (
                    <div className="bg-red-50 border border-red-200 text-red-800 p-4 rounded">
                      <strong>Error:</strong> {testResult.error}
                    </div>
                  ) : (
                    <div className="bg-gray-50 border border-gray-200 p-4 rounded">
                      <div className="mb-3">
                        <strong>Output:</strong>
                        <pre className="text-sm mt-2 whitespace-pre-wrap">
                          {JSON.stringify(testResult.output, null, 2)}
                        </pre>
                      </div>
                      {testResult.inputTokens && (
                        <div className="text-sm text-gray-600">
                          <strong>Tokens:</strong> {testResult.inputTokens} in / {testResult.outputTokens} out
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>

            <div className="flex gap-3 mt-6 pt-4 border-t">
              <button
                onClick={handleExecuteTest}
                className="btn-primary"
              >
                Execute Test
              </button>
              <button
                onClick={() => {
                  setTestingAgent(null)
                  setTestResult(null)
                }}
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

export default AgentManagement
