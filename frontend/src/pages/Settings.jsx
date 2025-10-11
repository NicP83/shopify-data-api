import { useState, useEffect } from 'react'
import api from '../services/api'

function Settings() {
  const [activeTab, setActiveTab] = useState('ai')

  // AI Config State
  const [aiConfig, setAIConfig] = useState({
    model: '',
    maxTokens: 1024,
    temperature: 0.7
  })
  const [prompt, setPrompt] = useState('')
  const [availableModels, setAvailableModels] = useState([])
  const [originalAIConfig, setOriginalAIConfig] = useState(null)

  // Chatbot Config State
  const [chatbotConfig, setChatbotConfig] = useState({
    storeName: '',
    storeDescription: '',
    storeCategories: '',
    scopeInstructions: '',
    outOfScopeResponse: '',
    requireSearchBeforeRecommendation: true,
    enableProductSearch: true,
    maxSearchResults: 5,
    toneOfVoice: 'friendly',
    includeCartLinks: true,
    showPrices: true,
    showSkus: true,
    customInstructions: ''
  })
  const [originalChatbotConfig, setOriginalChatbotConfig] = useState(null)
  const [generatedPrompt, setGeneratedPrompt] = useState('')

  // UI State
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState(null)

  useEffect(() => {
    loadSettings()
  }, [activeTab])

  const loadSettings = async () => {
    try {
      setLoading(true)

      if (activeTab === 'ai') {
        const [configRes, promptRes, modelsRes] = await Promise.all([
          api.getAIConfig(),
          api.getSystemPrompt(),
          api.getAvailableModels()
        ])

        setAIConfig(configRes.data)
        setOriginalAIConfig(configRes.data)
        setPrompt(promptRes.data.prompt)
        setAvailableModels(modelsRes.data.models)
      } else if (activeTab === 'chatbot') {
        const [configRes, previewRes] = await Promise.all([
          api.getChatbotConfig(),
          api.previewSystemPrompt()
        ])

        setChatbotConfig(configRes.data)
        setOriginalChatbotConfig(configRes.data)
        setGeneratedPrompt(previewRes.data.prompt)
      }
    } catch (error) {
      console.error('Error loading settings:', error)
      setMessage({ type: 'error', text: 'Failed to load settings' })
    } finally {
      setLoading(false)
    }
  }

  const handleAISave = async () => {
    try {
      setSaving(true)
      setMessage(null)

      const response = await api.updateAIConfig(aiConfig)

      setOriginalAIConfig(aiConfig)
      setMessage({
        type: 'success',
        text: response.data.message || 'AI settings saved successfully (runtime only)'
      })
    } catch (error) {
      console.error('Error saving AI settings:', error)
      setMessage({
        type: 'error',
        text: error.response?.data?.message || 'Failed to save AI settings'
      })
    } finally {
      setSaving(false)
    }
  }

  const handleChatbotSave = async () => {
    try {
      setSaving(true)
      setMessage(null)

      const response = await api.updateChatbotConfig(chatbotConfig)

      setOriginalChatbotConfig(chatbotConfig)

      // Reload generated prompt
      const previewRes = await api.previewSystemPrompt()
      setGeneratedPrompt(previewRes.data.prompt)

      setMessage({
        type: 'success',
        text: response.data.message || 'Chatbot settings saved successfully (runtime only)'
      })
    } catch (error) {
      console.error('Error saving chatbot settings:', error)
      setMessage({
        type: 'error',
        text: error.response?.data?.message || 'Failed to save chatbot settings'
      })
    } finally {
      setSaving(false)
    }
  }

  const handleChatbotReset = async () => {
    try {
      setSaving(true)
      setMessage(null)

      const response = await api.resetChatbotConfig()

      setChatbotConfig(response.data.config)
      setOriginalChatbotConfig(response.data.config)

      // Reload generated prompt
      const previewRes = await api.previewSystemPrompt()
      setGeneratedPrompt(previewRes.data.prompt)

      setMessage({
        type: 'success',
        text: 'Chatbot configuration reset to defaults'
      })
    } catch (error) {
      console.error('Error resetting chatbot settings:', error)
      setMessage({
        type: 'error',
        text: 'Failed to reset chatbot settings'
      })
    } finally {
      setSaving(false)
    }
  }

  const handleAIReset = () => {
    setAIConfig(originalAIConfig)
    setMessage({ type: 'info', text: 'AI settings reset to last saved values' })
  }

  const handleChatbotCancel = () => {
    setChatbotConfig(originalChatbotConfig)
    setMessage({ type: 'info', text: 'Chatbot settings reset to last saved values' })
  }

  const hasAIChanges = () => {
    return JSON.stringify(aiConfig) !== JSON.stringify(originalAIConfig)
  }

  const hasChatbotChanges = () => {
    return JSON.stringify(chatbotConfig) !== JSON.stringify(originalChatbotConfig)
  }

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-gray-500">Loading settings...</div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-gray-900 mb-2">Settings</h1>
        <p className="text-gray-600">
          Manage AI chatbot configuration and behavior
        </p>
      </div>

      {/* Tabs */}
      <div className="border-b border-gray-200">
        <nav className="-mb-px flex space-x-8">
          <button
            onClick={() => setActiveTab('ai')}
            className={`py-4 px-1 border-b-2 font-medium text-sm ${
              activeTab === 'ai'
                ? 'border-primary-500 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            AI Parameters
          </button>
          <button
            onClick={() => setActiveTab('chatbot')}
            className={`py-4 px-1 border-b-2 font-medium text-sm ${
              activeTab === 'chatbot'
                ? 'border-primary-500 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            Chatbot Configuration
          </button>
        </nav>
      </div>

      {/* Message Alert */}
      {message && (
        <div className={`p-4 rounded-lg ${
          message.type === 'success' ? 'bg-green-100 text-green-800' :
          message.type === 'error' ? 'bg-red-100 text-red-800' :
          'bg-blue-100 text-blue-800'
        }`}>
          {message.text}
        </div>
      )}

      {/* AI Parameters Tab */}
      {activeTab === 'ai' && (
        <>
          <div className="card">
            <h2 className="text-xl font-semibold mb-6">AI Model Settings</h2>

            <div className="space-y-6">
              {/* Model Selection */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Model
                </label>
                <select
                  value={aiConfig.model}
                  onChange={(e) => setAIConfig({ ...aiConfig, model: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
                >
                  {availableModels.map((model) => (
                    <option key={model} value={model}>
                      {model}
                    </option>
                  ))}
                </select>
                <p className="mt-1 text-sm text-gray-500">
                  Select which Claude model to use for chat responses
                </p>
              </div>

              {/* Temperature Slider */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Temperature: {aiConfig.temperature.toFixed(2)}
                </label>
                <input
                  type="range"
                  min="0"
                  max="1"
                  step="0.05"
                  value={aiConfig.temperature}
                  onChange={(e) => setAIConfig({ ...aiConfig, temperature: parseFloat(e.target.value) })}
                  className="w-full"
                />
                <div className="flex justify-between text-xs text-gray-500 mt-1">
                  <span>More Focused (0.0)</span>
                  <span>More Creative (1.0)</span>
                </div>
                <p className="mt-2 text-sm text-gray-500">
                  Controls randomness in responses. Lower values are more focused and deterministic
                </p>
              </div>

              {/* Max Tokens */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Max Tokens
                </label>
                <input
                  type="number"
                  min="256"
                  max="4096"
                  step="256"
                  value={aiConfig.maxTokens}
                  onChange={(e) => setAIConfig({ ...aiConfig, maxTokens: parseInt(e.target.value) })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
                />
                <p className="mt-1 text-sm text-gray-500">
                  Maximum length of AI responses (256-4096 tokens)
                </p>
              </div>

              {/* Action Buttons */}
              <div className="flex gap-3 pt-4 border-t">
                <button
                  onClick={handleAISave}
                  disabled={saving || !hasAIChanges()}
                  className="btn-primary disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {saving ? 'Saving...' : 'Save Changes'}
                </button>
                <button
                  onClick={handleAIReset}
                  disabled={!hasAIChanges()}
                  className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  Reset
                </button>
              </div>

              <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
                <p className="text-sm text-yellow-800">
                  <strong>Note:</strong> Runtime changes only. Restart resets to defaults.
                </p>
              </div>
            </div>
          </div>

          {/* System Prompt Display */}
          <div className="card">
            <h2 className="text-xl font-semibold mb-4">Generated System Prompt</h2>
            <div className="bg-gray-50 rounded-lg p-4 border border-gray-200">
              <pre className="text-sm text-gray-700 whitespace-pre-wrap font-mono">
                {prompt}
              </pre>
            </div>
            <div className="mt-4 bg-blue-50 border border-blue-200 rounded-lg p-4">
              <p className="text-sm text-blue-800">
                This prompt is dynamically generated from your chatbot configuration.
                Use the <strong>Chatbot Configuration</strong> tab to customize it.
              </p>
            </div>
          </div>
        </>
      )}

      {/* Chatbot Configuration Tab */}
      {activeTab === 'chatbot' && (
        <>
          {/* Store Identity */}
          <div className="card">
            <h2 className="text-xl font-semibold mb-6">Store Identity</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Store Name
                </label>
                <input
                  type="text"
                  value={chatbotConfig.storeName}
                  onChange={(e) => setChatbotConfig({ ...chatbotConfig, storeName: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
                  placeholder="e.g., Hearn's Hobbies"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Store Description
                </label>
                <input
                  type="text"
                  value={chatbotConfig.storeDescription}
                  onChange={(e) => setChatbotConfig({ ...chatbotConfig, storeDescription: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
                  placeholder="e.g., An online hobby store specializing in model kits"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Product Categories
                </label>
                <input
                  type="text"
                  value={chatbotConfig.storeCategories}
                  onChange={(e) => setChatbotConfig({ ...chatbotConfig, storeCategories: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
                  placeholder="e.g., model kits, hobby paints, tools"
                />
                <p className="mt-1 text-sm text-gray-500">Comma-separated list of product categories</p>
              </div>
            </div>
          </div>

          {/* Behavior Rules */}
          <div className="card">
            <h2 className="text-xl font-semibold mb-6">Behavior Rules</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Scope Instructions
                </label>
                <textarea
                  value={chatbotConfig.scopeInstructions}
                  onChange={(e) => setChatbotConfig({ ...chatbotConfig, scopeInstructions: e.target.value })}
                  rows="3"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
                  placeholder="Describe what products your store sells and specializes in"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Out-of-Scope Response
                </label>
                <textarea
                  value={chatbotConfig.outOfScopeResponse}
                  onChange={(e) => setChatbotConfig({ ...chatbotConfig, outOfScopeResponse: e.target.value })}
                  rows="2"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
                  placeholder="What to say when asked about products you don't carry"
                />
              </div>

              <div className="flex items-center">
                <input
                  type="checkbox"
                  id="requireSearch"
                  checked={chatbotConfig.requireSearchBeforeRecommendation}
                  onChange={(e) => setChatbotConfig({ ...chatbotConfig, requireSearchBeforeRecommendation: e.target.checked })}
                  className="h-4 w-4 text-primary-600 focus:ring-primary-500 border-gray-300 rounded"
                />
                <label htmlFor="requireSearch" className="ml-2 block text-sm text-gray-700">
                  Always search catalog before making recommendations
                </label>
              </div>
            </div>
          </div>

          {/* Tool Configuration */}
          <div className="card">
            <h2 className="text-xl font-semibold mb-6">Tool Configuration</h2>
            <div className="space-y-4">
              <div className="flex items-center">
                <input
                  type="checkbox"
                  id="enableSearch"
                  checked={chatbotConfig.enableProductSearch}
                  onChange={(e) => setChatbotConfig({ ...chatbotConfig, enableProductSearch: e.target.checked })}
                  className="h-4 w-4 text-primary-600 focus:ring-primary-500 border-gray-300 rounded"
                />
                <label htmlFor="enableSearch" className="ml-2 block text-sm text-gray-700">
                  Enable product search tool
                </label>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Max Search Results: {chatbotConfig.maxSearchResults}
                </label>
                <input
                  type="range"
                  min="1"
                  max="20"
                  value={chatbotConfig.maxSearchResults}
                  onChange={(e) => setChatbotConfig({ ...chatbotConfig, maxSearchResults: parseInt(e.target.value) })}
                  className="w-full"
                />
                <p className="mt-1 text-sm text-gray-500">
                  Maximum number of products returned per search
                </p>
              </div>
            </div>
          </div>

          {/* Response Style */}
          <div className="card">
            <h2 className="text-xl font-semibold mb-6">Response Style</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Tone of Voice
                </label>
                <select
                  value={chatbotConfig.toneOfVoice}
                  onChange={(e) => setChatbotConfig({ ...chatbotConfig, toneOfVoice: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
                >
                  <option value="professional">Professional</option>
                  <option value="friendly">Friendly</option>
                  <option value="enthusiastic">Enthusiastic</option>
                  <option value="casual">Casual</option>
                </select>
              </div>

              <div className="space-y-2">
                <div className="flex items-center">
                  <input
                    type="checkbox"
                    id="includeCartLinks"
                    checked={chatbotConfig.includeCartLinks}
                    onChange={(e) => setChatbotConfig({ ...chatbotConfig, includeCartLinks: e.target.checked })}
                    className="h-4 w-4 text-primary-600 focus:ring-primary-500 border-gray-300 rounded"
                  />
                  <label htmlFor="includeCartLinks" className="ml-2 block text-sm text-gray-700">
                    Include 'Add to Cart' links in responses
                  </label>
                </div>

                <div className="flex items-center">
                  <input
                    type="checkbox"
                    id="showPrices"
                    checked={chatbotConfig.showPrices}
                    onChange={(e) => setChatbotConfig({ ...chatbotConfig, showPrices: e.target.checked })}
                    className="h-4 w-4 text-primary-600 focus:ring-primary-500 border-gray-300 rounded"
                  />
                  <label htmlFor="showPrices" className="ml-2 block text-sm text-gray-700">
                    Show product prices
                  </label>
                </div>

                <div className="flex items-center">
                  <input
                    type="checkbox"
                    id="showSkus"
                    checked={chatbotConfig.showSkus}
                    onChange={(e) => setChatbotConfig({ ...chatbotConfig, showSkus: e.target.checked })}
                    className="h-4 w-4 text-primary-600 focus:ring-primary-500 border-gray-300 rounded"
                  />
                  <label htmlFor="showSkus" className="ml-2 block text-sm text-gray-700">
                    Show SKU information
                  </label>
                </div>
              </div>
            </div>
          </div>

          {/* Custom Instructions */}
          <div className="card">
            <h2 className="text-xl font-semibold mb-6">Custom Instructions</h2>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Additional Instructions (Optional)
              </label>
              <textarea
                value={chatbotConfig.customInstructions}
                onChange={(e) => setChatbotConfig({ ...chatbotConfig, customInstructions: e.target.value })}
                rows="4"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
                placeholder="Add any additional custom instructions for the AI..."
              />
              <p className="mt-1 text-sm text-gray-500">
                Optional custom instructions that will be appended to the system prompt
              </p>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="card">
            <div className="flex gap-3">
              <button
                onClick={handleChatbotSave}
                disabled={saving || !hasChatbotChanges()}
                className="btn-primary disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {saving ? 'Saving...' : 'Save Changes'}
              </button>
              <button
                onClick={handleChatbotCancel}
                disabled={!hasChatbotChanges()}
                className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Cancel
              </button>
              <button
                onClick={handleChatbotReset}
                disabled={saving}
                className="px-4 py-2 border border-red-300 text-red-700 rounded-lg hover:bg-red-50 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Reset to Defaults
              </button>
            </div>
            <div className="mt-4 bg-yellow-50 border border-yellow-200 rounded-lg p-4">
              <p className="text-sm text-yellow-800">
                <strong>Note:</strong> Runtime changes only. Restart resets to application.yml defaults.
              </p>
            </div>
          </div>

          {/* Generated Prompt Preview */}
          <div className="card">
            <h2 className="text-xl font-semibold mb-4">Generated System Prompt Preview</h2>
            <div className="bg-gray-50 rounded-lg p-4 border border-gray-200 max-h-96 overflow-y-auto">
              <pre className="text-sm text-gray-700 whitespace-pre-wrap font-mono">
                {generatedPrompt}
              </pre>
            </div>
            <p className="mt-2 text-sm text-gray-500">
              This is the system prompt that will be sent to Claude based on your configuration
            </p>
          </div>
        </>
      )}
    </div>
  )
}

export default Settings
