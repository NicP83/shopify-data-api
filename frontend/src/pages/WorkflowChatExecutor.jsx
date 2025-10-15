import { useState, useEffect, useRef } from 'react'
import { useParams } from 'react-router-dom'
import api from '../services/api'

/**
 * WorkflowChatExecutor - Chat-based workflow execution
 *
 * Provides a conversational interface for workflow execution
 * Users provide inputs through natural conversation
 */
function WorkflowChatExecutor() {
  const { id } = useParams()
  const messagesEndRef = useRef(null)

  const [workflow, setWorkflow] = useState(null)
  const [messages, setMessages] = useState([])
  const [currentInput, setCurrentInput] = useState('')
  const [collectedData, setCollectedData] = useState({})
  const [currentField, setCurrentField] = useState(null)
  const [loading, setLoading] = useState(true)
  const [executing, setExecuting] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    loadWorkflow()
  }, [id])

  useEffect(() => {
    scrollToBottom()
  }, [messages])

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  const loadWorkflow = async () => {
    try {
      setLoading(true)
      const response = await api.getWorkflow(id)
      setWorkflow(response.data)

      // Initialize chat with greeting
      const greeting = {
        type: 'bot',
        text: `Hi! I'll help you execute the "${response.data.name}" workflow. Let me collect the information I need.`,
        timestamp: new Date()
      }
      setMessages([greeting])

      // Start collecting fields
      if (response.data.inputSchemaJson?.properties) {
        const fields = Object.entries(response.data.inputSchemaJson.properties)
        if (fields.length > 0) {
          promptNextField(fields[0], response.data.inputSchemaJson.required)
        } else {
          // No fields, ready to execute
          addBotMessage("This workflow doesn't require any inputs. Type 'execute' to run it.")
        }
      }
    } catch (error) {
      console.error('Error loading workflow:', error)
      setError('Failed to load workflow')
    } finally {
      setLoading(false)
    }
  }

  const promptNextField = (fieldEntry, required = []) => {
    if (!fieldEntry) return

    const [fieldName, fieldSchema] = fieldEntry
    setCurrentField({ name: fieldName, schema: fieldSchema, required: required.includes(fieldName) })

    let promptText = fieldSchema.title || fieldName
    if (fieldSchema.description) {
      promptText += ` (${fieldSchema.description})`
    }
    if (required.includes(fieldName)) {
      promptText += ' *required*'
    }

    // Add type hints
    if (fieldSchema.enum) {
      promptText += `\nOptions: ${fieldSchema.enum.join(', ')}`
    } else if (fieldSchema.type === 'boolean') {
      promptText += '\nPlease answer yes or no'
    }

    addBotMessage(promptText)
  }

  const addBotMessage = (text) => {
    setMessages(prev => [...prev, {
      type: 'bot',
      text,
      timestamp: new Date()
    }])
  }

  const addUserMessage = (text) => {
    setMessages(prev => [...prev, {
      type: 'user',
      text,
      timestamp: new Date()
    }])
  }

  const handleSendMessage = async () => {
    if (!currentInput.trim()) return

    const userMessage = currentInput.trim()
    addUserMessage(userMessage)
    setCurrentInput('')

    // Check if user wants to execute
    if (userMessage.toLowerCase() === 'execute' && !currentField) {
      await executeWorkflow()
      return
    }

    // Process current field
    if (currentField) {
      const processedValue = processInputValue(userMessage, currentField.schema)

      if (processedValue === null && currentField.required) {
        addBotMessage("Invalid input. Please try again.")
        return
      }

      // Store the value
      setCollectedData(prev => ({
        ...prev,
        [currentField.name]: processedValue
      }))

      // Move to next field
      const fields = Object.entries(workflow.inputSchemaJson.properties)
      const currentIndex = fields.findIndex(([name]) => name === currentField.name)

      if (currentIndex < fields.length - 1) {
        // More fields to collect
        promptNextField(fields[currentIndex + 1], workflow.inputSchemaJson.required)
      } else {
        // All fields collected
        setCurrentField(null)
        addBotMessage("Great! I've collected all the information. Type 'execute' to run the workflow, or 'review' to see what I've collected.")
      }
    } else {
      // Handle commands
      if (userMessage.toLowerCase() === 'review') {
        const review = Object.entries(collectedData)
          .map(([key, value]) => `${key}: ${value}`)
          .join('\n')
        addBotMessage(`Here's what I've collected:\n\n${review}\n\nType 'execute' to run the workflow.`)
      } else {
        addBotMessage("I'm not sure what you mean. Type 'execute' to run the workflow or 'review' to see collected data.")
      }
    }
  }

  const processInputValue = (input, fieldSchema) => {
    try {
      switch (fieldSchema.type) {
        case 'string':
          if (fieldSchema.enum) {
            // Validate enum
            const match = fieldSchema.enum.find(opt =>
              opt.toLowerCase() === input.toLowerCase()
            )
            return match || null
          }
          return input

        case 'number':
        case 'integer':
          const num = fieldSchema.type === 'integer' ? parseInt(input) : parseFloat(input)
          return isNaN(num) ? null : num

        case 'boolean':
          const lowerInput = input.toLowerCase()
          if (['yes', 'true', '1', 'y'].includes(lowerInput)) return true
          if (['no', 'false', '0', 'n'].includes(lowerInput)) return false
          return null

        default:
          return input
      }
    } catch (error) {
      return null
    }
  }

  const executeWorkflow = async () => {
    try {
      setExecuting(true)
      addBotMessage("Executing workflow...")

      const response = workflow.isPublic
        ? await api.executePublicWorkflow(id, collectedData)
        : await api.executeWorkflow(id, collectedData)

      if (response.data.success) {
        addBotMessage("✅ Workflow executed successfully!")
        if (response.data.context) {
          const resultText = JSON.stringify(response.data.context, null, 2)
          addBotMessage(`Result:\n\`\`\`json\n${resultText}\n\`\`\``)
        }
      } else {
        addBotMessage(`❌ Execution failed: ${response.data.error || 'Unknown error'}`)
      }
    } catch (error) {
      console.error('Error executing workflow:', error)
      addBotMessage(`❌ Error: ${error.response?.data?.error || error.message}`)
    } finally {
      setExecuting(false)
    }
  }

  const handleKeyPress = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSendMessage()
    }
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto mb-4"></div>
          <p className="text-gray-600">Loading workflow...</p>
        </div>
      </div>
    )
  }

  if (!workflow) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="card max-w-md">
          <div className="text-center text-red-600">
            <h2 className="text-xl font-semibold mb-2">Workflow Not Found</h2>
            <p className="text-gray-600">The requested workflow could not be loaded.</p>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      {/* Header */}
      <div className="bg-white border-b border-gray-200 px-4 py-4">
        <div className="max-w-4xl mx-auto flex items-center gap-4">
          <div className="flex-shrink-0">
            <div className="w-10 h-10 bg-primary-100 rounded-lg flex items-center justify-center">
              <svg className="w-5 h-5 text-primary-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
              </svg>
            </div>
          </div>
          <div className="flex-1">
            <h1 className="text-lg font-bold text-gray-900">{workflow.name}</h1>
            <p className="text-sm text-gray-500">Chat Interface</p>
          </div>
          {workflow.isPublic && (
            <span className="px-2 py-1 bg-blue-100 text-blue-700 text-xs font-semibold rounded">
              Public
            </span>
          )}
        </div>
      </div>

      {/* Chat Messages */}
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-4xl mx-auto px-4 py-6 space-y-4">
          {messages.map((message, index) => (
            <div
              key={index}
              className={`flex ${message.type === 'user' ? 'justify-end' : 'justify-start'}`}
            >
              <div
                className={`max-w-[70%] rounded-lg px-4 py-3 ${
                  message.type === 'user'
                    ? 'bg-primary-600 text-white'
                    : 'bg-white border border-gray-200 text-gray-900'
                }`}
              >
                <p className="text-sm whitespace-pre-wrap">{message.text}</p>
                <p className={`text-xs mt-1 ${
                  message.type === 'user' ? 'text-primary-100' : 'text-gray-400'
                }`}>
                  {message.timestamp.toLocaleTimeString()}
                </p>
              </div>
            </div>
          ))}

          {executing && (
            <div className="flex justify-start">
              <div className="bg-white border border-gray-200 rounded-lg px-4 py-3">
                <div className="flex items-center gap-2">
                  <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-primary-600"></div>
                  <p className="text-sm text-gray-600">Processing...</p>
                </div>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>
      </div>

      {/* Input Area */}
      <div className="bg-white border-t border-gray-200 px-4 py-4">
        <div className="max-w-4xl mx-auto flex gap-2">
          <input
            type="text"
            value={currentInput}
            onChange={(e) => setCurrentInput(e.target.value)}
            onKeyPress={handleKeyPress}
            disabled={executing}
            placeholder="Type your response..."
            className="flex-1 px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500 disabled:bg-gray-100"
          />
          <button
            onClick={handleSendMessage}
            disabled={!currentInput.trim() || executing}
            className="btn-primary px-6 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
            </svg>
          </button>
        </div>

        {error && (
          <div className="max-w-4xl mx-auto mt-2">
            <p className="text-sm text-red-600">{error}</p>
          </div>
        )}
      </div>
    </div>
  )
}

export default WorkflowChatExecutor
