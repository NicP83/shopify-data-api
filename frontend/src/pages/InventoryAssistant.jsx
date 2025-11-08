import { useState, useEffect, useRef } from 'react'
import api from '../services/api'
import ChatMessageBubble from '../components/ChatMessageBubble'
import ChatInput from '../components/ChatInput'

/**
 * InventoryAssistant - Conversational AI interface for inventory management
 *
 * Features:
 * - Natural language queries for inventory analysis
 * - Contextual filters (brand, category, timeframe)
 * - Real-time AI responses with tool execution
 * - Human-in-the-loop order planning
 */
function InventoryAssistant() {
  // Chat State
  const [messages, setMessages] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const messagesEndRef = useRef(null)

  // Context State
  const [brands, setBrands] = useState([])
  const [categories, setCategories] = useState([])
  const [selectedBrand, setSelectedBrand] = useState('')
  const [selectedCategory, setSelectedCategory] = useState('')
  const [targetDays, setTargetDays] = useState(30)

  // Load initial data
  useEffect(() => {
    loadMetadata()

    // Welcome message
    setMessages([{
      role: 'assistant',
      content: `Hello! I'm your Inventory Management Assistant. I can help you with:

• Analyzing stock levels by brand, category, or supplier
• Finding low stock items that need attention
• Recommending order quantities based on sales velocity
• Planning orders for specific timeframes (e.g., "30 days of stock")
• Comparing products and predicting stockouts

Try asking: "Show me low stock Gundam products" or "What should I order for 2 weeks of stock?"`,
      timestamp: Date.now()
    }])
  }, [])

  // Auto-scroll
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  // Load brands and categories
  const loadMetadata = async () => {
    try {
      const [brandsRes, categoriesRes] = await Promise.all([
        api.getInventoryBrands(),
        api.getInventoryCategories()
      ])

      if (brandsRes.data?.success) setBrands(brandsRes.data.data || [])
      if (categoriesRes.data?.success) setCategories(categoriesRes.data.data || [])
    } catch (err) {
      console.error('Error loading metadata:', err)
    }
  }

  // Handle sending message
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
      // Build context with selected filters
      const context = {
        defaultBrand: selectedBrand,
        defaultCategory: selectedCategory,
        defaultDays: targetDays
      }

      // Call the Inventory Agent API
      const response = await api.sendInventoryAgentMessage(message, messages, context)

      if (response.data?.success && response.data?.data) {
        const data = response.data.data

        // Add assistant response
        const assistantMessage = {
          role: 'assistant',
          content: data.response || 'No response received',
          timestamp: Date.now(),
          toolCalls: data.toolCalls || []
        }

        setMessages(prev => [...prev, assistantMessage])
      } else {
        throw new Error('Invalid response format')
      }
    } catch (err) {
      console.error('Error sending message:', err)
      setError('Failed to process your request. Please try again.')

      const errorMessage = {
        role: 'assistant',
        content: 'I apologize, but I encountered an error processing your request. Please try again or rephrase your question.',
        timestamp: Date.now()
      }
      setMessages(prev => [...prev, errorMessage])
    } finally {
      setLoading(false)
    }
  }

  // Clear chat
  const handleClearChat = () => {
    setMessages([{
      role: 'assistant',
      content: 'Chat cleared! How can I help you with inventory management?',
      timestamp: Date.now()
    }])
    setError(null)
  }

  return (
    <div className="max-w-full mx-auto">
      {/* Header */}
      <div className="bg-white rounded-lg shadow-sm p-6 mb-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 mb-2">
              🤖 Inventory Assistant
            </h1>
            <p className="text-gray-600">
              Ask me anything about your inventory in natural language
            </p>
          </div>
          <button
            onClick={handleClearChat}
            className="bg-gray-200 text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-300 transition-colors"
          >
            Clear Chat
          </button>
        </div>

        {/* Context Filters */}
        <div className="mt-4 grid grid-cols-3 gap-4">
          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1">
              Default Brand Filter
            </label>
            <select
              value={selectedBrand}
              onChange={(e) => setSelectedBrand(e.target.value)}
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
            >
              <option value="">All Brands</option>
              {brands.map(brand => (
                <option key={brand} value={brand}>{brand}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1">
              Default Category Filter
            </label>
            <select
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
            >
              <option value="">All Categories</option>
              {categories.map(category => (
                <option key={category} value={category}>{category}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1">
              Default Stock Days
            </label>
            <select
              value={targetDays}
              onChange={(e) => setTargetDays(parseInt(e.target.value))}
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
            >
              <option value={7}>1 week</option>
              <option value={14}>2 weeks</option>
              <option value={30}>1 month</option>
              <option value={60}>2 months</option>
              <option value={90}>3 months</option>
            </select>
          </div>
        </div>
      </div>

      {/* Chat Interface */}
      <div className="bg-white rounded-lg shadow-sm flex flex-col" style={{ height: 'calc(100vh - 380px)' }}>
        {/* Messages Area */}
        <div className="flex-1 overflow-y-auto p-6">
          {messages.map((message, index) => (
            <div key={index} className="mb-4">
              <ChatMessageBubble message={message} />

              {/* Show tool calls if available */}
              {message.toolCalls && message.toolCalls.length > 0 && (
                <div className="mt-2 ml-12 space-y-2">
                  {message.toolCalls.map((toolCall, idx) => (
                    <div key={idx} className="bg-blue-50 border border-blue-200 rounded-lg p-3">
                      <div className="text-xs font-semibold text-blue-900 mb-1">
                        🔧 Tool: {toolCall.tool}
                      </div>
                      <div className="text-xs text-blue-700">
                        {JSON.stringify(toolCall.parameters || {})}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
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

      {/* Example Queries */}
      <div className="mt-4 bg-blue-50 border border-blue-200 rounded-lg p-4">
        <h3 className="text-sm font-semibold text-blue-900 mb-2">💡 Example Questions:</h3>
        <div className="grid grid-cols-2 gap-2 text-xs text-blue-800">
          <div>• "Show me critical low stock alerts"</div>
          <div>• "What Tamiya products are running low?"</div>
          <div>• "Analyze paint category inventory"</div>
          <div>• "What should I order for 1 month of stock?"</div>
          <div>• "Show me pending orders for Bandai"</div>
          <div>• "Predict stockout for SKU TAM-1234"</div>
        </div>
      </div>
    </div>
  )
}

export default InventoryAssistant
