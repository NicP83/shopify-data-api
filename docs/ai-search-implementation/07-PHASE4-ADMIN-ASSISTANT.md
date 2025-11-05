# Phase 4: Admin Product Search Assistant
## Dual-Mode Search Page for Internal Use

**Duration:** 1.5-2 days (10-14 hours)

**Duration:** 1.5-2 days (10-14 hours)

---

## Overview

Phase 4 creates an internal admin tool for staff to search products using two modes:

1. **Quick Search Mode** (🔍) - Direct product search (fast, keyword-based)
2. **AI Assistant Mode** (🤖) - Conversational search with recommendations

This tool helps staff:
- Quickly lookup products for customer service
- Get AI-powered product recommendations
- Find products using natural language
- Compare quick search vs AI search results

---

## Architecture

```
Admin Dashboard (/admin/product-search)
├─ Mode Toggle: [🔍 Quick Search] [🤖 AI Assistant]
├─ Search Input
└─ Results Display
    ├─ Quick Mode → Product Grid (instant)
    └─ AI Mode → Chat Interface (conversational)
```

---

## Step 1: Product Search Assistant Page

### ProductSearchAssistant.jsx

**File:** `/frontend/src/pages/ProductSearchAssistant.jsx`

```jsx
import { useState } from 'react'
import api from '../services/api'
import ChatProductCard from '../components/ChatProductCard'
import SearchModeToggle from '../components/SearchModeToggle'
import ChatMessageBubble from '../components/ChatMessageBubble'

function ProductSearchAssistant() {
  // Mode state
  const [searchMode, setSearchMode] = useState('quick') // 'quick' or 'ai'

  // Quick search state
  const [quickQuery, setQuickQuery] = useState('')
  const [quickResults, setQuickResults] = useState([])
  const [quickLoading, setQuickLoading] = useState(false)

  // AI search state
  const [aiMessages, setAiMessages] = useState([
    {
      role: 'assistant',
      content: 'Hello! I can help you find products. What are you looking for?',
      products: []
    }
  ])
  const [aiInput, setAiInput] = useState('')
  const [aiLoading, setAiLoading] = useState(false)

  /**
   * Handle quick search
   */
  const handleQuickSearch = async (e) => {
    e?.preventDefault()

    if (!quickQuery.trim() || quickLoading) return

    try {
      setQuickLoading(true)
      const response = await api.searchProducts({ query: quickQuery, limit: 12 })
      setQuickResults(response.data.products || [])
    } catch (error) {
      console.error('Quick search error:', error)
      alert('Search failed. Please try again.')
    } finally {
      setQuickLoading(false)
    }
  }

  /**
   * Handle AI search message
   */
  const handleAiSearch = async (e) => {
    e.preventDefault()

    if (!aiInput.trim() || aiLoading) return

    const userMessage = aiInput.trim()
    setAiInput('')

    // Add user message to chat
    const newMessages = [...aiMessages, { role: 'user', content: userMessage }]
    setAiMessages(newMessages)

    try {
      setAiLoading(true)

      // Build conversation history for API
      const conversationHistory = newMessages.map(msg => ({
        role: msg.role,
        content: msg.content
      }))

      // Call chat API
      const response = await api.chat({
        message: userMessage,
        conversationHistory: conversationHistory.slice(0, -1) // Exclude current message
      })

      // Add assistant response
      setAiMessages([
        ...newMessages,
        {
          role: 'assistant',
          content: response.data.response,
          products: response.data.products || []
        }
      ])

    } catch (error) {
      console.error('AI search error:', error)
      setAiMessages([
        ...newMessages,
        {
          role: 'assistant',
          content: 'Sorry, I encountered an error. Please try again.',
          products: []
        }
      ])
    } finally {
      setAiLoading(false)
    }
  }

  /**
   * Clear quick search results
   */
  const clearQuickSearch = () => {
    setQuickQuery('')
    setQuickResults([])
  }

  /**
   * Reset AI chat
   */
  const resetAiChat = () => {
    setAiMessages([
      {
        role: 'assistant',
        content: 'Hello! I can help you find products. What are you looking for?',
        products: []
      }
    ])
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-gray-900 mb-2">
          Product Search Assistant
        </h1>
        <p className="text-gray-600">
          Search products using quick keyword search or AI-powered recommendations
        </p>
      </div>

      {/* Mode Toggle */}
      <SearchModeToggle mode={searchMode} onModeChange={setSearchMode} />

      {/* Quick Search Mode */}
      {searchMode === 'quick' && (
        <div className="space-y-4">
          {/* Search Input */}
          <form onSubmit={handleQuickSearch} className="flex gap-2">
            <input
              type="text"
              value={quickQuery}
              onChange={(e) => setQuickQuery(e.target.value)}
              placeholder="Search by title, tags, or vendor..."
              className="flex-1 px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
            />
            <button
              type="submit"
              disabled={quickLoading}
              className="btn-primary disabled:opacity-50"
            >
              {quickLoading ? 'Searching...' : '🔍 Search'}
            </button>
            {quickResults.length > 0 && (
              <button
                type="button"
                onClick={clearQuickSearch}
                className="px-4 py-3 border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                Clear
              </button>
            )}
          </form>

          {/* Results */}
          {quickResults.length > 0 ? (
            <div>
              <div className="flex justify-between items-center mb-4">
                <h2 className="text-xl font-semibold">
                  Found {quickResults.length} products
                </h2>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {quickResults.map((product) => (
                  <ChatProductCard key={product.id} product={product} />
                ))}
              </div>
            </div>
          ) : (
            <div className="text-center py-12 text-gray-500">
              {quickLoading ? 'Searching...' : 'Enter a search term to find products'}
            </div>
          )}
        </div>
      )}

      {/* AI Assistant Mode */}
      {searchMode === 'ai' && (
        <div className="card">
          {/* Chat Messages */}
          <div className="h-[500px] overflow-y-auto mb-4 space-y-4">
            {aiMessages.map((message, index) => (
              <ChatMessageBubble key={index} message={message} />
            ))}

            {/* Loading indicator */}
            {aiLoading && (
              <div className="flex items-start gap-3">
                <div className="w-8 h-8 rounded-full bg-gradient-to-br from-primary-500 to-purple-600 flex items-center justify-center text-white flex-shrink-0">
                  🤖
                </div>
                <div className="bg-gray-100 rounded-lg px-4 py-3">
                  <div className="flex gap-2">
                    <div className="w-2 h-2 rounded-full bg-gray-400 animate-bounce"></div>
                    <div className="w-2 h-2 rounded-full bg-gray-400 animate-bounce delay-100"></div>
                    <div className="w-2 h-2 rounded-full bg-gray-400 animate-bounce delay-200"></div>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Input Form */}
          <form onSubmit={handleAiSearch} className="flex gap-2">
            <input
              type="text"
              value={aiInput}
              onChange={(e) => setAiInput(e.target.value)}
              placeholder="Ask me about products..."
              className="flex-1 px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
              disabled={aiLoading}
            />
            <button
              type="submit"
              disabled={aiLoading || !aiInput.trim()}
              className="btn-primary disabled:opacity-50"
            >
              {aiLoading ? 'Sending...' : '→'}
            </button>
            <button
              type="button"
              onClick={resetAiChat}
              className="px-4 py-3 border border-gray-300 rounded-lg hover:bg-gray-50"
            >
              Reset
            </button>
          </form>
        </div>
      )}
    </div>
  )
}

export default ProductSearchAssistant
```

---

## Step 2: Search Mode Toggle Component

### SearchModeToggle.jsx

**File:** `/frontend/src/components/SearchModeToggle.jsx`

```jsx
function SearchModeToggle({ mode, onModeChange }) {
  return (
    <div className="flex items-center gap-4 p-1 bg-gray-100 rounded-lg w-fit">
      <button
        onClick={() => onModeChange('quick')}
        className={`
          flex items-center gap-2 px-4 py-2 rounded-lg font-medium transition-all
          ${mode === 'quick'
            ? 'bg-white text-primary-600 shadow-sm'
            : 'text-gray-600 hover:text-gray-900'
          }
        `}
      >
        <span className="text-lg">🔍</span>
        Quick Search
      </button>

      <button
        onClick={() => onModeChange('ai')}
        className={`
          flex items-center gap-2 px-4 py-2 rounded-lg font-medium transition-all
          ${mode === 'ai'
            ? 'bg-white text-primary-600 shadow-sm'
            : 'text-gray-600 hover:text-gray-900'
          }
        `}
      >
        <span className="text-lg">🤖</span>
        AI Assistant
      </button>
    </div>
  )
}

export default SearchModeToggle
```

---

## Step 3: Chat Product Card Component

### ChatProductCard.jsx

**File:** `/frontend/src/components/ChatProductCard.jsx`

```jsx
function ChatProductCard({ product }) {
  return (
    <a
      href={product.url || `https://hearnshobbies.com/products/${product.handle}`}
      target="_blank"
      rel="noopener noreferrer"
      className="block bg-white border border-gray-200 rounded-lg overflow-hidden hover:shadow-lg transition-shadow"
    >
      {/* Product Image */}
      <div className="aspect-square bg-gray-100">
        {product.image ? (
          <img
            src={product.image}
            alt={product.title}
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-gray-400">
            No image
          </div>
        )}
      </div>

      {/* Product Info */}
      <div className="p-4">
        <h3 className="font-semibold text-gray-900 mb-1 line-clamp-2">
          {product.title}
        </h3>

        {product.vendor && (
          <p className="text-sm text-gray-600 mb-2">{product.vendor}</p>
        )}

        <div className="flex items-center justify-between">
          <span className="text-lg font-bold text-primary-600">
            {product.currency || 'USD'} ${product.price}
          </span>

          {product.inStock !== undefined && (
            <span className={`
              text-xs font-medium px-2 py-1 rounded-full
              ${product.inStock
                ? 'bg-green-100 text-green-800'
                : 'bg-red-100 text-red-800'
              }
            `}>
              {product.inStock ? 'In Stock' : 'Out of Stock'}
            </span>
          )}
        </div>

        {product.tags && product.tags.length > 0 && (
          <div className="flex flex-wrap gap-1 mt-3">
            {product.tags.slice(0, 3).map((tag, index) => (
              <span
                key={index}
                className="text-xs bg-gray-100 text-gray-700 px-2 py-1 rounded"
              >
                {tag}
              </span>
            ))}
          </div>
        )}
      </div>
    </a>
  )
}

export default ChatProductCard
```

---

## Step 4: Update Chat Message Bubble

### ChatMessageBubble.jsx Enhancement

**File:** `/frontend/src/components/ChatMessageBubble.jsx`

Update to handle product rendering:

```jsx
import ChatProductCard from './ChatProductCard'

function ChatMessageBubble({ message }) {
  const { role, content, products = [] } = message

  return (
    <div className={`flex items-start gap-3 ${role === 'user' ? 'flex-row-reverse' : ''}`}>
      {/* Avatar (assistant only) */}
      {role === 'assistant' && (
        <div className="w-8 h-8 rounded-full bg-gradient-to-br from-primary-500 to-purple-600 flex items-center justify-center text-white flex-shrink-0">
          🤖
        </div>
      )}

      {/* Message Content */}
      <div className={`flex-1 ${role === 'user' ? 'flex justify-end' : ''}`}>
        <div className={`
          max-w-2xl rounded-lg px-4 py-3
          ${role === 'user'
            ? 'bg-primary-600 text-white'
            : 'bg-gray-100 text-gray-900'
          }
        `}>
          {/* Text content */}
          <div
            className="prose prose-sm max-w-none"
            dangerouslySetInnerHTML={{ __html: formatContent(content) }}
          />

          {/* Product cards */}
          {products.length > 0 && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mt-4">
              {products.map((product) => (
                <ChatProductCard key={product.id} product={product} />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

/**
 * Format message content (convert markdown to HTML)
 */
function formatContent(content) {
  return content
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>') // Bold
    .replace(/\n/g, '<br>') // Line breaks
    .replace(/^- (.+)$/gm, '<li>$1</li>') // List items
    .replace(/(<li>.*<\/li>)/s, '<ul class="list-disc pl-4 my-2">$1</ul>') // Wrap lists
}

export default ChatMessageBubble
```

---

## Step 5: Add Route

### Update App.jsx

**File:** `/frontend/src/App.jsx`

```jsx
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import Layout from './components/Layout'
import ProductSearchAssistant from './pages/ProductSearchAssistant'
// ... other imports

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Layout />}>
          {/* ... existing routes ... */}
          <Route path="/product-search" element={<ProductSearchAssistant />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
```

### Update Navigation

Add link to sidebar navigation:

```jsx
<NavLink to="/product-search">
  <span className="text-xl">🔍</span>
  Product Search
</NavLink>
```

---

## Step 6: API Methods

### Update api.js

**File:** `/frontend/src/services/api.js`

```javascript
// Add product search method
searchProducts: (params) => {
  return axios.post('/api/products/search', params)
},

// Update chat method to support shop parameter
chat: (data, shopDomain = null) => {
  const url = shopDomain
    ? `/api/shopify/chat/message?shop=${shopDomain}`
    : '/api/chat/message'

  return axios.post(url, data)
}
```

---

## Step 7: Testing

### Test Quick Search Mode

1. **Navigate to `/product-search`**
2. **Enter "gundam" in search**
3. **Click "🔍 Search"**
4. **Verify:**
   - Results appear instantly (< 500ms)
   - Product cards show images, prices, titles
   - Cards are clickable

### Test AI Assistant Mode

1. **Toggle to "🤖 AI Assistant"**
2. **Type: "Show me beginner Gundam kits"**
3. **Verify:**
   - Message appears in chat
   - Loading indicator shown
   - AI response appears with product cards
   - Product cards rendered in chat bubble

### Test Mode Switching

1. **Switch between modes**
2. **Verify:**
   - State is preserved (quick search results don't disappear)
   - Smooth transition
   - No errors in console

---

## Phase 4 Checklist

- [ ] ProductSearchAssistant page created
- [ ] SearchModeToggle component implemented
- [ ] ChatProductCard component created
- [ ] ChatMessageBubble updated for product rendering
- [ ] Route added to App.jsx
- [ ] Navigation link added
- [ ] API methods updated
- [ ] Quick search mode functional
- [ ] AI assistant mode functional
- [ ] Mode toggle works correctly
- [ ] Product cards render in AI chat
- [ ] Product links work
- [ ] Mobile responsive design
- [ ] Tested on multiple screen sizes

---

## Next Phase

**Phase 5: Integration Testing** - End-to-end testing of all components.

---

*Last Updated: 2025-10-30*
*Next: 08-PHASE5-TESTING.md*
