import React from 'react'

function ChatMessageBubble({ message }) {
  const isUser = message.role === 'user'
  const timestamp = message.timestamp ? new Date(message.timestamp).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit'
  }) : ''

  // Parse content to detect and highlight cart links
  const renderContent = (content) => {
    // Detect URLs in the content
    const urlRegex = /(https?:\/\/[^\s]+)/g
    const parts = content.split(urlRegex)

    return parts.map((part, index) => {
      if (part.match(urlRegex)) {
        // Check if it's a cart link
        const isCartLink = part.includes('/cart/')
        return (
          <a
            key={index}
            href={part}
            target="_blank"
            rel="noopener noreferrer"
            className={`underline ${isCartLink ? 'text-blue-600 font-semibold' : 'text-blue-500'}`}
          >
            {isCartLink ? '🛒 Add to Cart' : part}
          </a>
        )
      }
      return <span key={index}>{part}</span>
    })
  }

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'} mb-4`}>
      <div className={`max-w-3/4 ${isUser ? 'order-2' : 'order-1'}`}>
        <div
          className={`rounded-lg px-4 py-3 ${
            isUser
              ? 'bg-primary-600 text-white'
              : 'bg-gray-200 text-gray-900'
          }`}
        >
          <div className="whitespace-pre-wrap break-words">
            {renderContent(message.content)}
          </div>
        </div>
        {timestamp && (
          <div className={`text-xs text-gray-500 mt-1 ${isUser ? 'text-right' : 'text-left'}`}>
            {timestamp}
          </div>
        )}
      </div>
      {!isUser && (
        <div className="w-8 h-8 rounded-full bg-primary-100 flex items-center justify-center mr-2 order-0">
          <span className="text-primary-600">🤖</span>
        </div>
      )}
      {isUser && (
        <div className="w-8 h-8 rounded-full bg-gray-300 flex items-center justify-center ml-2 order-3">
          <span>👤</span>
        </div>
      )}
    </div>
  )
}

export default ChatMessageBubble
