import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import api from '../services/api'

function AgentEditor() {
  const { id } = useParams()
  const navigate = useNavigate()
  const isEditMode = !!id

  // Agent state
  const [agent, setAgent] = useState({
    name: '',
    description: '',
    modelProvider: 'ANTHROPIC',
    modelName: 'claude-3-5-sonnet-20241022',
    systemPrompt: '',
    temperature: 0.7,
    maxTokens: 4096,
    isActive: true
  })

  // Tools state
  const [allTools, setAllTools] = useState([])
  const [selectedToolIds, setSelectedToolIds] = useState([])

  // UI state
  const [loading, setLoading] = useState(isEditMode)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  const modelOptions = {
    ANTHROPIC: [
      'claude-3-5-sonnet-20241022',
      'claude-3-5-haiku-20241022',
      'claude-3-opus-20240229',
      'claude-3-sonnet-20240229',
      'claude-3-haiku-20240307'
    ],
    OPENAI: [
      'gpt-4-turbo-preview',
      'gpt-4',
      'gpt-3.5-turbo'
    ],
    GOOGLE: [
      'gemini-pro',
      'gemini-pro-vision'
    ]
  }

  useEffect(() => {
    loadTools()
    if (isEditMode) {
      loadAgent()
    }
  }, [id])

  const loadTools = async () => {
    try {
      const response = await api.getTools()
      setAllTools(response.data)
    } catch (error) {
      console.error('Error loading tools:', error)
      setError('Failed to load tools')
    }
  }

  const loadAgent = async () => {
    try {
      setLoading(true)
      const response = await api.getAgent(id)
      setAgent(response.data)

      // Extract tool IDs from agentTools relationship
      if (response.data.agentTools) {
        const toolIds = response.data.agentTools.map(at => at.tool.id)
        setSelectedToolIds(toolIds)
      }
    } catch (error) {
      console.error('Error loading agent:', error)
      setError('Failed to load agent')
    } finally {
      setLoading(false)
    }
  }

  const handleSave = async () => {
    try {
      setSaving(true)
      setError(null)

      if (!agent.name.trim()) {
        setError('Agent name is required')
        return
      }

      if (!agent.systemPrompt.trim()) {
        setError('System prompt is required')
        return
      }

      const agentData = {
        ...agent,
        toolIds: selectedToolIds
      }

      if (isEditMode) {
        await api.updateAgent(id, agentData)
        alert('Agent updated successfully')
      } else {
        const response = await api.createAgent(agentData)
        navigate(`/agents/${response.data.id}`)
        return
      }
    } catch (error) {
      console.error('Error saving agent:', error)
      setError(error.response?.data?.message || 'Failed to save agent')
    } finally {
      setSaving(false)
    }
  }

  const handleToolToggle = (toolId) => {
    setSelectedToolIds(prev =>
      prev.includes(toolId)
        ? prev.filter(id => id !== toolId)
        : [...prev, toolId]
    )
  }

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-gray-500">Loading agent...</div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-start">
        <div>
          <h1 className="text-3xl font-bold text-gray-900 mb-2">
            {isEditMode ? 'Edit Agent' : 'Create Agent'}
          </h1>
          <p className="text-gray-600">
            {isEditMode ? `Editing: ${agent.name}` : 'Create a new AI agent with custom capabilities'}
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => navigate('/agents')}
            className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            disabled={saving}
            className="btn-primary disabled:opacity-50"
          >
            {saving ? 'Saving...' : 'Save Agent'}
          </button>
        </div>
      </div>

      {/* Error Message */}
      {error && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded">
          {error}
        </div>
      )}

      {/* Basic Information */}
      <div className="card">
        <h2 className="text-xl font-semibold mb-6">Basic Information</h2>
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Agent Name *
            </label>
            <input
              type="text"
              value={agent.name}
              onChange={(e) => setAgent({ ...agent, name: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
              placeholder="e.g., Customer Support Agent"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Description
            </label>
            <textarea
              value={agent.description}
              onChange={(e) => setAgent({ ...agent, description: e.target.value })}
              rows="3"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
              placeholder="Describe what this agent does..."
            />
          </div>

          <div className="flex items-center">
            <input
              type="checkbox"
              id="isActive"
              checked={agent.isActive}
              onChange={(e) => setAgent({ ...agent, isActive: e.target.checked })}
              className="h-4 w-4 text-primary-600 focus:ring-primary-500 border-gray-300 rounded"
            />
            <label htmlFor="isActive" className="ml-2 block text-sm text-gray-700">
              Active (agent will be available for use)
            </label>
          </div>
        </div>
      </div>

      {/* Model Configuration */}
      <div className="card">
        <h2 className="text-xl font-semibold mb-6">Model Configuration</h2>
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Model Provider
              </label>
              <select
                value={agent.modelProvider}
                onChange={(e) => setAgent({
                  ...agent,
                  modelProvider: e.target.value,
                  modelName: modelOptions[e.target.value][0]
                })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
              >
                <option value="ANTHROPIC">Anthropic (Claude)</option>
                <option value="OPENAI">OpenAI (GPT)</option>
                <option value="GOOGLE">Google (Gemini)</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Model
              </label>
              <select
                value={agent.modelName}
                onChange={(e) => setAgent({ ...agent, modelName: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
              >
                {modelOptions[agent.modelProvider].map((model) => (
                  <option key={model} value={model}>
                    {model}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Temperature: {agent.temperature.toFixed(2)}
            </label>
            <input
              type="range"
              min="0"
              max="1"
              step="0.05"
              value={agent.temperature}
              onChange={(e) => setAgent({ ...agent, temperature: parseFloat(e.target.value) })}
              className="w-full"
            />
            <div className="flex justify-between text-xs text-gray-500 mt-1">
              <span>More Focused (0.0)</span>
              <span>More Creative (1.0)</span>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Max Tokens
            </label>
            <input
              type="number"
              min="256"
              max="8192"
              step="256"
              value={agent.maxTokens}
              onChange={(e) => setAgent({ ...agent, maxTokens: parseInt(e.target.value) })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
            />
            <p className="mt-1 text-sm text-gray-500">
              Maximum length of AI responses (256-8192 tokens)
            </p>
          </div>
        </div>
      </div>

      {/* System Prompt */}
      <div className="card">
        <h2 className="text-xl font-semibold mb-6">System Prompt *</h2>
        <div>
          <textarea
            value={agent.systemPrompt}
            onChange={(e) => setAgent({ ...agent, systemPrompt: e.target.value })}
            rows="12"
            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 font-mono text-sm"
            placeholder="You are a helpful AI assistant that..."
          />
          <p className="mt-2 text-sm text-gray-500">
            This prompt defines the agent's behavior, personality, and capabilities. Be specific and clear.
          </p>
        </div>
      </div>

      {/* Tool Assignment */}
      <div className="card">
        <h2 className="text-xl font-semibold mb-6">Tool Assignment</h2>
        <p className="text-sm text-gray-600 mb-4">
          Select which tools this agent can use. Tools extend the agent's capabilities beyond conversation.
        </p>

        {allTools.length === 0 ? (
          <div className="text-center py-8 text-gray-500">
            No tools available. Create tools first to assign them to agents.
          </div>
        ) : (
          <div className="space-y-3">
            {allTools.map((tool) => (
              <div
                key={tool.id}
                className="flex items-start p-3 border border-gray-200 rounded-lg hover:border-primary-300"
              >
                <input
                  type="checkbox"
                  id={`tool-${tool.id}`}
                  checked={selectedToolIds.includes(tool.id)}
                  onChange={() => handleToolToggle(tool.id)}
                  className="mt-1 h-4 w-4 text-primary-600 focus:ring-primary-500 border-gray-300 rounded"
                />
                <label htmlFor={`tool-${tool.id}`} className="ml-3 flex-1 cursor-pointer">
                  <div className="font-medium text-gray-900">{tool.name}</div>
                  {tool.description && (
                    <div className="text-sm text-gray-600 mt-1">{tool.description}</div>
                  )}
                  <div className="flex gap-2 mt-2">
                    <span className={`text-xs px-2 py-1 rounded-full ${
                      tool.isActive
                        ? 'bg-green-100 text-green-800'
                        : 'bg-gray-100 text-gray-800'
                    }`}>
                      {tool.isActive ? 'Active' : 'Inactive'}
                    </span>
                    <span className="text-xs px-2 py-1 rounded-full bg-blue-100 text-blue-800">
                      {tool.handlerClass}
                    </span>
                  </div>
                </label>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Save Button (bottom) */}
      <div className="flex justify-end gap-2">
        <button
          onClick={() => navigate('/agents')}
          className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
        >
          Cancel
        </button>
        <button
          onClick={handleSave}
          disabled={saving}
          className="btn-primary disabled:opacity-50"
        >
          {saving ? 'Saving...' : 'Save Agent'}
        </button>
      </div>
    </div>
  )
}

export default AgentEditor
