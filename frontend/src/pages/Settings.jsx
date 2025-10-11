import { useState, useEffect } from 'react'
import api from '../services/api'

function Settings() {
  const [config, setConfig] = useState({
    model: '',
    maxTokens: 1024,
    temperature: 0.7
  })
  const [prompt, setPrompt] = useState('')
  const [availableModels, setAvailableModels] = useState([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState(null)
  const [originalConfig, setOriginalConfig] = useState(null)

  useEffect(() => {
    loadSettings()
  }, [])

  const loadSettings = async () => {
    try {
      setLoading(true)
      const [configRes, promptRes, modelsRes] = await Promise.all([
        api.getAIConfig(),
        api.getSystemPrompt(),
        api.getAvailableModels()
      ])

      setConfig(configRes.data)
      setOriginalConfig(configRes.data)
      setPrompt(promptRes.data.prompt)
      setAvailableModels(modelsRes.data.models)
    } catch (error) {
      console.error('Error loading settings:', error)
      setMessage({ type: 'error', text: 'Failed to load settings' })
    } finally {
      setLoading(false)
    }
  }

  const handleSave = async () => {
    try {
      setSaving(true)
      setMessage(null)

      const response = await api.updateAIConfig(config)

      setOriginalConfig(config)
      setMessage({
        type: 'success',
        text: response.data.message || 'Settings saved successfully (runtime only)'
      })
    } catch (error) {
      console.error('Error saving settings:', error)
      setMessage({
        type: 'error',
        text: error.response?.data?.message || 'Failed to save settings'
      })
    } finally {
      setSaving(false)
    }
  }

  const handleReset = () => {
    setConfig(originalConfig)
    setMessage({ type: 'info', text: 'Settings reset to last saved values' })
  }

  const hasChanges = () => {
    return JSON.stringify(config) !== JSON.stringify(originalConfig)
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
        <h1 className="text-3xl font-bold text-gray-900 mb-2">AI Configuration</h1>
        <p className="text-gray-600">
          Manage Claude AI chatbot settings and behavior
        </p>
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

      {/* Configuration Form */}
      <div className="card">
        <h2 className="text-xl font-semibold mb-6">AI Parameters</h2>

        <div className="space-y-6">
          {/* Model Selection */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Model
            </label>
            <select
              value={config.model}
              onChange={(e) => setConfig({ ...config, model: e.target.value })}
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
              Temperature: {config.temperature.toFixed(2)}
            </label>
            <input
              type="range"
              min="0"
              max="1"
              step="0.05"
              value={config.temperature}
              onChange={(e) => setConfig({ ...config, temperature: parseFloat(e.target.value) })}
              className="w-full"
            />
            <div className="flex justify-between text-xs text-gray-500 mt-1">
              <span>More Focused (0.0)</span>
              <span>More Creative (1.0)</span>
            </div>
            <p className="mt-2 text-sm text-gray-500">
              Controls randomness in responses. Lower values are more focused and deterministic, higher values are more creative
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
              value={config.maxTokens}
              onChange={(e) => setConfig({ ...config, maxTokens: parseInt(e.target.value) })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
            />
            <p className="mt-1 text-sm text-gray-500">
              Maximum length of AI responses (256-4096 tokens, ~200-3000 words)
            </p>
          </div>

          {/* Action Buttons */}
          <div className="flex gap-3 pt-4 border-t">
            <button
              onClick={handleSave}
              disabled={saving || !hasChanges()}
              className="btn-primary disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {saving ? 'Saving...' : 'Save Changes'}
            </button>
            <button
              onClick={handleReset}
              disabled={!hasChanges()}
              className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              Reset
            </button>
          </div>

          <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
            <p className="text-sm text-yellow-800">
              <strong>Note:</strong> Settings changes are applied at runtime only and will reset when the server restarts.
              To make permanent changes, update the environment variables or application.yml file.
            </p>
          </div>
        </div>
      </div>

      {/* System Prompt Display */}
      <div className="card">
        <h2 className="text-xl font-semibold mb-4">System Prompt</h2>
        <div className="bg-gray-50 rounded-lg p-4 border border-gray-200">
          <pre className="text-sm text-gray-700 whitespace-pre-wrap font-mono">
            {prompt}
          </pre>
        </div>
        <div className="mt-4 bg-blue-50 border border-blue-200 rounded-lg p-4">
          <p className="text-sm text-blue-800">
            <strong>To edit the system prompt:</strong> Modify the file at{' '}
            <code className="bg-blue-100 px-2 py-1 rounded">
              src/main/resources/prompts/system-prompt.txt
            </code>{' '}
            and restart the server.
          </p>
        </div>
      </div>

      {/* Help Section */}
      <div className="card bg-gray-50">
        <h2 className="text-xl font-semibold mb-4">Configuration Guide</h2>
        <div className="space-y-4 text-sm text-gray-700">
          <div>
            <h3 className="font-semibold text-gray-900 mb-1">Model Selection</h3>
            <ul className="list-disc list-inside space-y-1 ml-2">
              <li><strong>claude-3-5-sonnet</strong> - Best balance of performance and cost</li>
              <li><strong>claude-3-5-haiku</strong> - Fastest, most cost-effective</li>
              <li><strong>claude-3-opus</strong> - Highest capability for complex tasks</li>
            </ul>
          </div>
          <div>
            <h3 className="font-semibold text-gray-900 mb-1">Temperature Settings</h3>
            <ul className="list-disc list-inside space-y-1 ml-2">
              <li><strong>0.0-0.3</strong> - Focused, consistent responses for customer support</li>
              <li><strong>0.4-0.7</strong> - Balanced creativity for general chat</li>
              <li><strong>0.8-1.0</strong> - Creative, varied responses for marketing</li>
            </ul>
          </div>
          <div>
            <h3 className="font-semibold text-gray-900 mb-1">Max Tokens</h3>
            <ul className="list-disc list-inside space-y-1 ml-2">
              <li><strong>512-1024</strong> - Short, concise responses</li>
              <li><strong>1024-2048</strong> - Standard detailed responses</li>
              <li><strong>2048-4096</strong> - Comprehensive, in-depth explanations</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  )
}

export default Settings
