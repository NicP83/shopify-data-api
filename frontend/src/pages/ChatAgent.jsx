import React, { useState, useEffect, useRef } from 'react'
import api from '../services/api'
import ChatMessageBubble from '../components/ChatMessageBubble'
import ChatInput from '../components/ChatInput'

function ChatAgent() {
  const [messages, setMessages] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [chatStatus, setChatStatus] = useState(null)
  const messagesEndRef = useRef(null)

  // Scroll to bottom when new messages arrive
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages])

  // Load chat status on mount
  useEffect(() => {
    loadChatStatus()
    // Add welcome message
    setMessages([{
      role: 'assistant',
      content: 'Hello! 👋 I\'m your Gundam store assistant. I can help you find the perfect model kit, answer questions about products, and generate direct purchase links. How can I assist you today?',
      timestamp: Date.now()
    }])
  }, [])

  const loadChatStatus = async () => {
    try {
      const response = await api.getChatStatus()
      if (response.data.success) {
        setChatStatus(response.data.data)
      }
    } catch (err) {
      console.error('Error loading chat status:', err)
    }
  }

  const handleSendMessage = async (message) => {
    // Add user message to conversation
    const userMessage = {
      role: 'user',
      content: message,
      timestamp: Date.now()
    }

    setMessages(prev => [...prev, userMessage])
    setLoading(true)
    setError(null)

    try {
      // Send to API with conversation history
      const response = await api.sendChatMessage(message, messages)

      if (response.data.success && response.data.data) {
        // Add assistant response
        const assistantMessage = {
          role: 'assistant',
          content: response.data.data.content,
          timestamp: response.data.data.timestamp || Date.now()
        }
        setMessages(prev => [...prev, assistantMessage])
      } else {
        setError('Failed to get response from assistant')
      }
    } catch (err) {
      console.error('Error sending message:', err)
      setError('An error occurred while sending your message. Please try again.')

      // Add error message to chat
      const errorMessage = {
        role: 'assistant',
        content: 'I apologize, but I\'m having trouble processing your request. Please try again or use the Product Search page to browse our catalog.',
        timestamp: Date.now()
      }
      setMessages(prev => [...prev, errorMessage])
    } finally {
      setLoading(false)
    }
  }

  const handleClearChat = () => {
    setMessages([{
      role: 'assistant',
      content: 'Chat cleared! How can I help you today?',
      timestamp: Date.now()
    }])
    setError(null)
  }

  return (
    <div className="max-w-5xl mx-auto">
      {/* Header */}
      <div className="bg-white rounded-lg shadow-sm p-6 mb-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 mb-2">
              🤖 AI Chat Assistant
            </h1>
            <p className="text-gray-600">
              Ask about products, get recommendations, and receive direct purchase links
            </p>
          </div>
          <button
            onClick={handleClearChat}
            className="bg-gray-200 text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-300 transition-colors"
          >
            Clear Chat
          </button>
        </div>

        {chatStatus && (
          <div className="mt-4 p-3 bg-primary-50 rounded-lg">
            <div className="flex items-center text-sm text-primary-800">
              <span className="w-2 h-2 bg-green-500 rounded-full mr-2"></span>
              <span className="font-semibold">{chatStatus.provider}</span>
              <span className="mx-2">•</span>
              <span>{chatStatus.description}</span>
            </div>
          </div>
        )}
      </div>

      {/* Chat Messages Container */}
      <div className="bg-white rounded-lg shadow-sm flex flex-col" style={{ height: 'calc(100vh - 320px)' }}>
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

      {/* Suggested Prompts */}
      <div className="mt-6 bg-white rounded-lg shadow-sm p-6">
        <h3 className="font-semibold text-gray-900 mb-3">Try asking:</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
          {[
            'Show me popular Gundam models under $50',
            'What\'s the difference between HG and MG kits?',
            'I\'m a beginner, what model should I start with?',
            'Do you have any limited edition kits in stock?'
          ].map((prompt, index) => (
            <button
              key={index}
              onClick={() => !loading && handleSendMessage(prompt)}
              disabled={loading}
              className="text-left p-3 border border-gray-200 rounded-lg hover:bg-gray-50 hover:border-primary-300 transition-colors text-sm text-gray-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {prompt}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}

export default ChatAgent
