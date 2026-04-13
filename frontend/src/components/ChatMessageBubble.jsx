import React from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

function ChatMessageBubble({ message }) {
  const isUser = message.role === 'user'
  const timestamp = message.timestamp ? new Date(message.timestamp).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit'
  }) : ''

  const linkRenderer = ({ href, children }) => {
    const isCartLink = href?.includes('/cart/')
    const isProductLink = href?.includes('/products/')

    if (isCartLink) {
      return (
        <a href={href} target="_blank" rel="noopener noreferrer"
          className="text-blue-600 font-bold underline">
          🛒 Add to Cart
        </a>
      )
    }
    if (isProductLink) {
      return (
        <a href={href} target="_blank" rel="noopener noreferrer"
          className="text-green-600 font-bold underline">
          🔍 View Product
        </a>
      )
    }
    return (
      <a href={href} target="_blank" rel="noopener noreferrer"
        className="text-blue-500 underline">
        {children}
      </a>
    )
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
          <div className="prose prose-sm max-w-none break-words prose-headings:mt-2 prose-headings:mb-1 prose-p:my-1 prose-table:my-2 prose-li:my-0">
            <ReactMarkdown remarkPlugins={[remarkGfm]} components={{ a: linkRenderer }}>
              {message.content}
            </ReactMarkdown>
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
