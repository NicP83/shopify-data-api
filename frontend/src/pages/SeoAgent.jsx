import { useState } from 'react'

/**
 * SeoAgent - Hybrid chat + settings interface for SEO tasks
 *
 * This is a placeholder page for the SEO Agent feature.
 * Full implementation will include:
 * - Chat interface (similar to AI Chat Agent)
 * - Settings panel for tool/agent selection
 * - LLM configuration options
 * - Main orchestration prompt
 *
 * See: docs/seo-agent/IMPLEMENTATION_PLAN.md for complete specifications
 */
function SeoAgent() {
  const [settingsOpen, setSettingsOpen] = useState(true)

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
          <button
            onClick={() => setSettingsOpen(!settingsOpen)}
            className="bg-gray-200 text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-300 transition-colors"
          >
            {settingsOpen ? 'Hide Settings' : 'Show Settings'}
          </button>
        </div>
      </div>

      {/* Main Content - Two Panel Layout */}
      <div className="flex gap-6" style={{ height: 'calc(100vh - 280px)' }}>
        {/* Chat Panel - Left */}
        <div className={`bg-white rounded-lg shadow-sm ${settingsOpen ? 'w-2/3' : 'w-full'} transition-all duration-300`}>
          <div className="p-6 h-full flex flex-col">
            <div className="flex-1 flex items-center justify-center text-gray-400">
              <div className="text-center max-w-md">
                <p className="text-6xl mb-4">💬</p>
                <p className="text-xl font-semibold text-gray-700 mb-2">Chat Interface</p>
                <p className="text-sm text-gray-500 mb-4">Coming in Phase 3</p>
                <div className="text-left text-sm text-gray-600 space-y-2 bg-gray-50 p-4 rounded-lg">
                  <p className="font-semibold">Planned features:</p>
                  <ul className="list-disc list-inside space-y-1">
                    <li>Real-time conversation with AI</li>
                    <li>Tool invocation display</li>
                    <li>Agent execution status</li>
                    <li>Message history</li>
                    <li>Streaming responses</li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Settings Panel - Right */}
        {settingsOpen && (
          <div className="w-1/3 bg-white rounded-lg shadow-sm transition-all duration-300">
            <div className="p-6 h-full overflow-y-auto">
              <h2 className="text-lg font-bold text-gray-900 mb-4">⚙️ Settings</h2>

              {/* Settings sections */}
              <div className="space-y-6">
                {/* Tool Selection */}
                <div className="pb-6 border-b border-gray-200">
                  <h3 className="font-semibold text-gray-900 mb-2">🔧 Tool Selection</h3>
                  <p className="text-sm text-gray-600 mb-3">
                    Select which tools the SEO Agent can use
                  </p>
                  <div className="bg-gray-50 p-3 rounded text-xs text-gray-500">
                    Multi-select dropdown coming in Phase 4
                  </div>
                </div>

                {/* Agent Selection */}
                <div className="pb-6 border-b border-gray-200">
                  <h3 className="font-semibold text-gray-900 mb-2">🤖 Agent Selection</h3>
                  <p className="text-sm text-gray-600 mb-3">
                    Select which agents can be invoked during conversation
                  </p>
                  <div className="bg-gray-50 p-3 rounded text-xs text-gray-500">
                    Multi-select dropdown coming in Phase 4
                  </div>
                </div>

                {/* LLM Configuration */}
                <div className="pb-6 border-b border-gray-200">
                  <h3 className="font-semibold text-gray-900 mb-2">🧠 LLM Configuration</h3>
                  <p className="text-sm text-gray-600 mb-3">
                    Configure AI model settings
                  </p>
                  <div className="bg-gray-50 p-3 rounded text-xs text-gray-500 space-y-1">
                    <div>• Model selection</div>
                    <div>• Temperature</div>
                    <div>• Max tokens</div>
                    <div>• Top P, frequency/presence penalties</div>
                    <div className="mt-2 italic">Coming in Phase 4</div>
                  </div>
                </div>

                {/* Main Orchestration Prompt */}
                <div>
                  <h3 className="font-semibold text-gray-900 mb-2">📝 Orchestration Prompt</h3>
                  <p className="text-sm text-gray-600 mb-3">
                    The system prompt that manages how AI uses tools and agents
                  </p>
                  <div className="bg-gray-50 p-3 rounded text-xs text-gray-500">
                    Large text area for prompt editing coming in Phase 4
                  </div>
                </div>
              </div>

              {/* Info Box */}
              <div className="mt-6 bg-blue-50 border border-blue-200 rounded-lg p-4">
                <div className="flex gap-2">
                  <svg className="w-5 h-5 text-blue-600 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  <div>
                    <h4 className="text-sm font-semibold text-blue-900 mb-1">Implementation Status</h4>
                    <p className="text-xs text-blue-800">
                      Phase 1 complete: Navigation reorganization and placeholder page.
                      See docs/seo-agent/IMPLEMENTATION_PLAN.md for full roadmap.
                    </p>
                  </div>
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
