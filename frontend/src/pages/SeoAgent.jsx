import { useState, useEffect, useRef } from 'react'
import api from '../services/api'
import ChatMessageBubble from '../components/ChatMessageBubble'
import ChatInput from '../components/ChatInput'

/**
 * SeoAgent - Hybrid chat + settings interface for SEO tasks
 *
 * Phase 3+4 Implementation:
 * - Chat interface with message history
 * - Settings panel with tool/agent selection
 * - LLM configuration
 * - Orchestration prompt editor
 * - Local storage persistence
 *
 * See: docs/seo-agent/IMPLEMENTATION_PLAN.md for complete specifications
 */
function SeoAgent() {
  // UI State
  const [settingsOpen, setSettingsOpen] = useState(true)

  // Chat State
  const [messages, setMessages] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const messagesEndRef = useRef(null)

  // Settings State - Tools & Agents
  const [availableTools, setAvailableTools] = useState([])
  const [availableAgents, setAvailableAgents] = useState([])
  const [selectedTools, setSelectedTools] = useState([])
  const [selectedAgents, setSelectedAgents] = useState([])

  // Settings State - LLM Configuration
  const [llmConfig, setLlmConfig] = useState({
    model: 'claude-3-5-sonnet-20241022',
    temperature: 0.7,
    maxTokens: 4096,
    topP: 1.0,
  })

  // Settings State - Orchestration Prompt
  const [orchestrationPrompt, setOrchestrationPrompt] = useState(
    `You are an SEO expert assistant with access to specialized tools and agents.

Your goal is to help users with SEO-related tasks such as:
- Keyword research and analysis
- Content optimization
- Technical SEO audits
- Competitor analysis
- Link building strategies

AVAILABLE TOOLS:
{tool_list}

AVAILABLE AGENTS:
{agent_list}

GUIDELINES:
1. Use tools for simple, single-purpose tasks
2. Use agents for complex, multi-step workflows
3. Always explain what you're doing before calling a tool or agent
4. Present results in a clear, actionable format
5. Provide specific recommendations backed by data

Engage with the user professionally and help them improve their SEO strategy.`
  )

  // Load data on mount
  useEffect(() => {
    loadToolsAndAgents()
    loadSavedConfig()

    // Add welcome message
    setMessages([{
      role: 'assistant',
      content: 'Hello! I\'m your SEO Agent assistant. I can help you with keyword research, content optimization, technical SEO, and more. Configure my available tools and agents in the settings panel, then ask me anything!',
      timestamp: Date.now()
    }])
  }, [])

  // Auto-scroll to bottom
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  // Load tools and agents from API
  const loadToolsAndAgents = async () => {
    try {
      // Load available tools
      const toolsResponse = await api.getAgentTools()
      setAvailableTools(toolsResponse.data || [])

      // Load available agents
      const agentsResponse = await api.getAgents()
      setAvailableAgents(agentsResponse.data || [])
    } catch (err) {
      console.error('Error loading tools/agents:', err)
    }
  }

  // Load saved configuration from localStorage
  const loadSavedConfig = () => {
    try {
      const saved = localStorage.getItem('seo-agent-config')
      if (saved) {
        const config = JSON.parse(saved)
        if (config.selectedTools) setSelectedTools(config.selectedTools)
        if (config.selectedAgents) setSelectedAgents(config.selectedAgents)
        if (config.llmConfig) setLlmConfig(config.llmConfig)
        if (config.orchestrationPrompt) setOrchestrationPrompt(config.orchestrationPrompt)
      }
    } catch (err) {
      console.error('Error loading saved config:', err)
    }
  }

  // Save configuration to localStorage
  const saveConfig = () => {
    try {
      const config = {
        selectedTools,
        selectedAgents,
        llmConfig,
        orchestrationPrompt,
        savedAt: Date.now()
      }
      localStorage.setItem('seo-agent-config', JSON.stringify(config))

      // Show success feedback
      const successMsg = {
        role: 'system',
        content: 'Configuration saved successfully!',
        timestamp: Date.now()
      }
      setMessages(prev => [...prev, successMsg])
      setTimeout(() => {
        setMessages(prev => prev.filter(m => m !== successMsg))
      }, 3000)
    } catch (err) {
      console.error('Error saving config:', err)
      setError('Failed to save configuration')
    }
  }

  // Handle sending a message
  const handleSendMessage = async (message) => {
    const userMessage = {
      role: 'user',
      content: message,
      timestamp: Date.now()
    }

    setMessages(prev => [...prev, userMessage])
    setLoading(true)
    setError(null)

    try {
      // Build config object for SEO Agent API
      const config = {
        selectedTools,
        selectedAgents,
        llmConfig,
        orchestrationPrompt
      }

      // Call the SEO Agent API with configuration
      const response = await api.sendSeoAgentMessage(message, messages, config)

      if (response.data.success && response.data.data) {
        const assistantMessage = {
          role: 'assistant',
          content: response.data.data.message.content,
          timestamp: response.data.data.message.timestamp || Date.now(),
          metadata: response.data.data.metadata
        }
        setMessages(prev => [...prev, assistantMessage])
      } else {
        setError('Failed to get response from SEO Agent')
      }
    } catch (err) {
      console.error('Error sending message:', err)
      setError('An error occurred while processing your request.')

      const errorMessage = {
        role: 'assistant',
        content: 'I apologize, but I encountered an error processing your request. Please try again or adjust the settings.',
        timestamp: Date.now()
      }
      setMessages(prev => [...prev, errorMessage])
    } finally {
      setLoading(false)
    }
  }

  // Handle clearing chat
  const handleClearChat = () => {
    setMessages([{
      role: 'assistant',
      content: 'Chat cleared! How can I help you with your SEO needs?',
      timestamp: Date.now()
    }])
    setError(null)
  }

  // Toggle tool selection
  const toggleTool = (toolId) => {
    setSelectedTools(prev =>
      prev.includes(toolId)
        ? prev.filter(id => id !== toolId)
        : [...prev, toolId]
    )
  }

  // Toggle agent selection
  const toggleAgent = (agentId) => {
    setSelectedAgents(prev =>
      prev.includes(agentId)
        ? prev.filter(id => id !== agentId)
        : [...prev, agentId]
    )
  }

  return (
    <div className="max-w-full mx-auto">
      {/* Header */}
      <div className="bg-white rounded-lg shadow-sm p-6 mb-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 mb-2">
              🎯 SEO Agent
            </h1>
            <p className="text-gray-600">
              AI-powered SEO assistant with configurable tools and agents
            </p>
          </div>
          <div className="flex gap-2">
            <button
              onClick={handleClearChat}
              className="bg-gray-200 text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-300 transition-colors text-sm"
            >
              Clear Chat
            </button>
            <button
              onClick={() => setSettingsOpen(!settingsOpen)}
              className="bg-primary-600 text-white px-4 py-2 rounded-lg hover:bg-primary-700 transition-colors"
            >
              {settingsOpen ? 'Hide Settings' : 'Show Settings'}
            </button>
          </div>
        </div>

        {/* Config Status */}
        <div className="mt-4 flex flex-wrap gap-2 text-xs">
          <span className="px-2 py-1 bg-blue-100 text-blue-800 rounded">
            {selectedTools.length} tools selected
          </span>
          <span className="px-2 py-1 bg-purple-100 text-purple-800 rounded">
            {selectedAgents.length} agents selected
          </span>
          <span className="px-2 py-1 bg-green-100 text-green-800 rounded">
            Model: {llmConfig.model.split('-').slice(-2).join(' ')}
          </span>
        </div>
      </div>

      {/* Main Content - Two Panel Layout */}
      <div className="flex gap-6" style={{ height: 'calc(100vh - 320px)' }}>
        {/* Chat Panel - Left */}
        <div className={`bg-white rounded-lg shadow-sm flex flex-col ${settingsOpen ? 'w-2/3' : 'w-full'} transition-all duration-300`}>
          {/* Messages Area */}
          <div className="flex-1 overflow-y-auto p-6">
            {messages.map((message, index) => (
              <ChatMessageBubble key={index} message={message} />
            ))}

            {loading && (
              <div className="flex justify-start mb-4">
                <div className="bg-gray-200 rounded-lg px-4 py-3">
                  <div className="flex items-center space-x-2">
                    <div className="w-2 h-2 bg-gray-500 rounded-full animate-bounce" style={{ animationDelay: '0ms' }}></div>
                    <div className="w-2 h-2 bg-gray-500 rounded-full animate-bounce" style={{ animationDelay: '150ms' }}></div>
                    <div className="w-2 h-2 bg-gray-500 rounded-full animate-bounce" style={{ animationDelay: '300ms' }}></div>
                  </div>
                </div>
              </div>
            )}

            {error && (
              <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-4">
                <p className="text-red-800 text-sm">{error}</p>
              </div>
            )}

            <div ref={messagesEndRef} />
          </div>

          {/* Input Area */}
          <ChatInput onSend={handleSendMessage} disabled={loading} />
        </div>

        {/* Settings Panel - Right */}
        {settingsOpen && (
          <div className="w-1/3 bg-white rounded-lg shadow-sm transition-all duration-300 flex flex-col">
            <div className="p-6 flex-1 overflow-y-auto">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-lg font-bold text-gray-900">⚙️ Settings</h2>
                <button
                  onClick={saveConfig}
                  className="text-xs bg-green-600 text-white px-3 py-1 rounded hover:bg-green-700 transition-colors"
                >
                  Save Config
                </button>
              </div>

              {/* Settings sections */}
              <div className="space-y-6">
                {/* Tool Selection */}
                <div className="pb-6 border-b border-gray-200">
                  <h3 className="font-semibold text-gray-900 mb-2">🔧 Tool Selection</h3>
                  <p className="text-sm text-gray-600 mb-3">
                    Select which tools the SEO Agent can use
                  </p>
                  <div className="space-y-2 max-h-40 overflow-y-auto">
                    {availableTools.length > 0 ? (
                      availableTools.map(tool => (
                        <label key={tool.id} className="flex items-start gap-2 p-2 hover:bg-gray-50 rounded cursor-pointer">
                          <input
                            type="checkbox"
                            checked={selectedTools.includes(tool.id)}
                            onChange={() => toggleTool(tool.id)}
                            className="mt-1"
                          />
                          <div className="flex-1">
                            <div className="text-sm font-medium text-gray-900">{tool.name}</div>
                            <div className="text-xs text-gray-500">{tool.description}</div>
                          </div>
                        </label>
                      ))
                    ) : (
                      <p className="text-xs text-gray-500 italic">No tools available</p>
                    )}
                  </div>
                </div>

                {/* Agent Selection */}
                <div className="pb-6 border-b border-gray-200">
                  <h3 className="font-semibold text-gray-900 mb-2">🤖 Agent Selection</h3>
                  <p className="text-sm text-gray-600 mb-3">
                    Select which agents can be invoked
                  </p>
                  <div className="space-y-2 max-h-40 overflow-y-auto">
                    {availableAgents.length > 0 ? (
                      availableAgents.map(agent => (
                        <label key={agent.id} className="flex items-start gap-2 p-2 hover:bg-gray-50 rounded cursor-pointer">
                          <input
                            type="checkbox"
                            checked={selectedAgents.includes(agent.id)}
                            onChange={() => toggleAgent(agent.id)}
                            className="mt-1"
                          />
                          <div className="flex-1">
                            <div className="text-sm font-medium text-gray-900">{agent.name}</div>
                            <div className="text-xs text-gray-500">{agent.description}</div>
                          </div>
                        </label>
                      ))
                    ) : (
                      <p className="text-xs text-gray-500 italic">No agents available</p>
                    )}
                  </div>
                </div>

                {/* LLM Configuration */}
                <div className="pb-6 border-b border-gray-200">
                  <h3 className="font-semibold text-gray-900 mb-2">🧠 LLM Configuration</h3>

                  {/* Model Selection */}
                  <div className="mb-4">
                    <label className="block text-sm text-gray-700 mb-1">Model</label>
                    <select
                      value={llmConfig.model}
                      onChange={(e) => setLlmConfig({...llmConfig, model: e.target.value})}
                      className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
                    >
                      <option value="claude-3-5-sonnet-20241022">Claude 3.5 Sonnet</option>
                      <option value="claude-3-opus-20240229">Claude 3 Opus</option>
                      <option value="claude-3-haiku-20240307">Claude 3 Haiku</option>
                    </select>
                  </div>

                  {/* Temperature */}
                  <div className="mb-4">
                    <label className="block text-sm text-gray-700 mb-1">
                      Temperature: {llmConfig.temperature}
                    </label>
                    <input
                      type="range"
                      min="0"
                      max="1"
                      step="0.1"
                      value={llmConfig.temperature}
                      onChange={(e) => setLlmConfig({...llmConfig, temperature: parseFloat(e.target.value)})}
                      className="w-full"
                    />
                  </div>

                  {/* Max Tokens */}
                  <div className="mb-4">
                    <label className="block text-sm text-gray-700 mb-1">Max Tokens</label>
                    <input
                      type="number"
                      min="1"
                      max="200000"
                      value={llmConfig.maxTokens}
                      onChange={(e) => setLlmConfig({...llmConfig, maxTokens: parseInt(e.target.value)})}
                      className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
                    />
                  </div>

                  {/* Top P */}
                  <div>
                    <label className="block text-sm text-gray-700 mb-1">
                      Top P: {llmConfig.topP}
                    </label>
                    <input
                      type="range"
                      min="0"
                      max="1"
                      step="0.1"
                      value={llmConfig.topP}
                      onChange={(e) => setLlmConfig({...llmConfig, topP: parseFloat(e.target.value)})}
                      className="w-full"
                    />
                  </div>
                </div>

                {/* Orchestration Prompt */}
                <div>
                  <h3 className="font-semibold text-gray-900 mb-2">📝 Orchestration Prompt</h3>
                  <p className="text-sm text-gray-600 mb-3">
                    System prompt that manages tool/agent usage
                  </p>
                  <textarea
                    rows={10}
                    value={orchestrationPrompt}
                    onChange={(e) => setOrchestrationPrompt(e.target.value)}
                    className="w-full border border-gray-300 rounded-lg px-3 py-2 text-xs font-mono"
                    placeholder="Enter orchestration prompt..."
                  />
                </div>
              </div>
            </div>

            {/* Info Box */}
            <div className="p-4 bg-green-50 border-t border-green-200">
              <div className="flex gap-2">
                <svg className="w-4 h-4 text-green-600 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <div>
                  <h4 className="text-xs font-semibold text-green-900 mb-1">Status</h4>
                  <p className="text-xs text-green-800">
                    Phase 5 complete! Backend API integrated with full tool/agent orchestration.
                  </p>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

export default SeoAgent
