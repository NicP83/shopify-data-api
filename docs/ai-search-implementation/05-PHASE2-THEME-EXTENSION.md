# Phase 2: Shopify Theme Extension
## Search Bar Enhancement and AI Chat Modal

**Duration:** 1-1.5 days (7-10 hours)

---

## Overview

Phase 2 creates the customer-facing Shopify theme extension that:

1. Adds AI button (🤖) next to the existing search bar
2. Opens AI chat modal when clicked
3. Renders product cards in chat responses
4. Handles mobile responsiveness
5. Integrates with backend chat API

---

## What is a Shopify Theme Extension?

A **Theme App Extension** is JavaScript/CSS code that runs on the Shopify storefront without modifying theme files directly. It's part of your Custom Shopify App and:

- ✅ Injects automatically when app is enabled
- ✅ No theme code editing required
- ✅ Updates automatically when you deploy
- ✅ Works with any Shopify theme
- ✅ Survives theme changes/updates

---

## Architecture

```
┌─────────────────────────────────────────────┐
│  hearnshobbies.com (Shopify Storefront)    │
├─────────────────────────────────────────────┤
│                                             │
│  Existing Search Bar                        │
│  ┌─────────────────────────┐               │
│  │ Search products...      │ [🔍] [🤖] ←── Extension adds this
│  └─────────────────────────┘               │
│                                             │
│  [Click 🤖]                                 │
│       ↓                                     │
│  ┌──────────────────────────────────────┐  │
│  │  AI Chat Modal (Overlay)             │  │
│  │  ┌────────────────────────────────┐  │  │
│  │  │ 🤖 How can I help?             │  │  │
│  │  │                                │  │  │
│  │  │ [Product Card]                 │  │  │
│  │  │ [Product Card]                 │  │  │
│  │  └────────────────────────────────┘  │  │
│  │  [Type message...            ] [→]  │  │
│  └──────────────────────────────────────┘  │
│                                             │
└─────────────────────────────────────────────┘
         │
         │ POST /api/shopify/chat/message
         ↓
   Backend API (Railway)
```

---

## Step 1: Extension Structure

### Directory Structure

```
/extensions/
  /search-enhancer/
    ├── shopify.extension.toml     # Extension config
    ├── assets/
    │   ├── search-enhancer.js     # Main widget JavaScript
    │   └── ai-chat-styles.css     # Widget styles
    └── snippets/
        └── ai-chat-button.liquid  # Liquid snippet (optional)
```

---

## Step 2: Extension Configuration

### shopify.extension.toml

**File:** `/extensions/search-enhancer/shopify.extension.toml`

```toml
api_version = "2024-01"
name = "AI Search Assistant"
type = "theme"

[extension_points]
  [[extension_points.entry]]
    target = "body_end"
    module = "./assets/search-enhancer.js"
    resource = "storefront"

  [[extension_points.entry]]
    target = "head"
    module = "./assets/ai-chat-styles.css"
    resource = "storefront"

[settings]
  [[settings.fields]]
    key = "enabled"
    type = "boolean"
    name = "Enable AI Assistant"
    default = true

  [[settings.fields]]
    key = "button_position"
    type = "select"
    name = "Button Position"
    options = [
      { label = "Next to Search", value = "inline" },
      { label = "Bottom Right", value = "fixed" }
    ]
    default = "inline"

  [[settings.fields]]
    key = "api_endpoint"
    type = "text"
    name = "API Endpoint"
    default = "https://your-app.railway.app"

[capabilities]
  network_access = true
  block_progress = false
```

---

## Step 3: Widget JavaScript

### search-enhancer.js

**File:** `/extensions/search-enhancer/assets/search-enhancer.js`

```javascript
/**
 * Hearn's Hobbies AI Search Assistant
 * Shopify Theme Extension Widget
 */

(function() {
  'use strict';

  // Configuration
  const CONFIG = {
    apiEndpoint: window.HearnsAI?.apiEndpoint || 'https://your-app.railway.app',
    shopDomain: Shopify.shop, // Shopify provides this globally
    buttonEmoji: '🤖',
    buttonText: 'AI Assistant',
    debug: false
  };

  // State
  let conversationHistory = [];
  let isModalOpen = false;
  let isLoading = false;

  /**
   * Initialize the extension
   */
  function init() {
    console.log('[HearnsAI] Initializing AI Search Assistant');

    // Wait for DOM to be ready
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', setup);
    } else {
      setup();
    }
  }

  /**
   * Setup the widget
   */
  function setup() {
    // Find the search bar
    const searchBar = findSearchBar();
    if (!searchBar) {
      console.warn('[HearnsAI] Search bar not found');
      return;
    }

    // Add AI button next to search
    addAiButton(searchBar);

    // Create chat modal (hidden)
    createChatModal();

    console.log('[HearnsAI] Widget initialized successfully');
  }

  /**
   * Find the search bar element (works with most themes)
   */
  function findSearchBar() {
    // Try common selectors (Dawn theme, Debut theme, etc.)
    const selectors = [
      'input[type="search"]',
      'input[name="q"]',
      '.search__input',
      '.predictive-search__input',
      '#Search-In-Modal',
      '[role="searchbox"]'
    ];

    for (const selector of selectors) {
      const element = document.querySelector(selector);
      if (element) {
        console.log('[HearnsAI] Found search bar:', selector);
        return element.closest('form') || element.parentElement;
      }
    }

    return null;
  }

  /**
   * Add AI button next to search bar
   */
  function addAiButton(searchBar) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'hearns-ai-button';
    button.innerHTML = `
      <span class="hearns-ai-icon">${CONFIG.buttonEmoji}</span>
      <span class="hearns-ai-label">${CONFIG.buttonText}</span>
    `;
    button.setAttribute('aria-label', 'Open AI Shopping Assistant');
    button.addEventListener('click', openChatModal);

    // Insert button after search input
    const searchInput = searchBar.querySelector('input[type="search"], input[name="q"]');
    if (searchInput) {
      searchInput.parentElement.style.position = 'relative';
      searchInput.parentElement.appendChild(button);
    } else {
      searchBar.appendChild(button);
    }
  }

  /**
   * Create chat modal HTML
   */
  function createChatModal() {
    const modal = document.createElement('div');
    modal.id = 'hearns-ai-modal';
    modal.className = 'hearns-ai-modal';
    modal.innerHTML = `
      <div class="hearns-ai-modal-overlay" data-close-modal></div>
      <div class="hearns-ai-modal-content">
        <div class="hearns-ai-modal-header">
          <h2>
            <span class="hearns-ai-icon">${CONFIG.buttonEmoji}</span>
            AI Shopping Assistant
          </h2>
          <button class="hearns-ai-close" data-close-modal aria-label="Close">
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M15 5L5 15M5 5L15 15" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </button>
        </div>
        <div class="hearns-ai-messages" id="hearns-ai-messages">
          <div class="hearns-ai-message hearns-ai-message-assistant">
            <div class="hearns-ai-avatar">${CONFIG.buttonEmoji}</div>
            <div class="hearns-ai-bubble">
              <p>Hello! I'm your AI shopping assistant. How can I help you find the perfect product today?</p>
            </div>
          </div>
        </div>
        <form class="hearns-ai-input-form" id="hearns-ai-form">
          <input
            type="text"
            class="hearns-ai-input"
            id="hearns-ai-input"
            placeholder="Ask me about products..."
            autocomplete="off"
          />
          <button type="submit" class="hearns-ai-send" aria-label="Send message">
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M2 10L18 2L10 18L9 11L2 10Z" fill="currentColor"/>
            </svg>
          </button>
        </form>
      </div>
    `;

    document.body.appendChild(modal);

    // Event listeners
    modal.querySelectorAll('[data-close-modal]').forEach(el => {
      el.addEventListener('click', closeChatModal);
    });

    document.getElementById('hearns-ai-form').addEventListener('submit', handleSendMessage);
  }

  /**
   * Open chat modal
   */
  function openChatModal(e) {
    if (e) e.preventDefault();

    const modal = document.getElementById('hearns-ai-modal');
    modal.classList.add('hearns-ai-modal-open');
    isModalOpen = true;

    // Focus input
    setTimeout(() => {
      document.getElementById('hearns-ai-input').focus();
    }, 100);
  }

  /**
   * Close chat modal
   */
  function closeChatModal() {
    const modal = document.getElementById('hearns-ai-modal');
    modal.classList.remove('hearns-ai-modal-open');
    isModalOpen = false;
  }

  /**
   * Handle send message form submission
   */
  async function handleSendMessage(e) {
    e.preventDefault();

    if (isLoading) return;

    const input = document.getElementById('hearns-ai-input');
    const message = input.value.trim();

    if (!message) return;

    // Clear input
    input.value = '';

    // Add user message to UI
    addMessageToUI('user', message);

    // Show loading indicator
    showLoadingIndicator();

    try {
      // Send to backend API
      const response = await sendMessageToAPI(message);

      // Hide loading indicator
      hideLoadingIndicator();

      // Add assistant response to UI
      addMessageToUI('assistant', response.response, response.products);

      // Update conversation history
      conversationHistory.push(
        { role: 'user', content: message },
        { role: 'assistant', content: response.response }
      );

    } catch (error) {
      hideLoadingIndicator();
      addMessageToUI('assistant', 'Sorry, I encountered an error. Please try again.', []);
      console.error('[HearnsAI] Error:', error);
    }
  }

  /**
   * Send message to backend API
   */
  async function sendMessageToAPI(message) {
    const url = `${CONFIG.apiEndpoint}/api/shopify/chat/message?shop=${CONFIG.shopDomain}`;

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        message: message,
        conversationHistory: conversationHistory
      })
    });

    if (!response.ok) {
      throw new Error(`API error: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * Add message to chat UI
   */
  function addMessageToUI(role, content, products = []) {
    const messagesContainer = document.getElementById('hearns-ai-messages');

    const messageDiv = document.createElement('div');
    messageDiv.className = `hearns-ai-message hearns-ai-message-${role}`;

    if (role === 'assistant') {
      messageDiv.innerHTML = `
        <div class="hearns-ai-avatar">${CONFIG.buttonEmoji}</div>
        <div class="hearns-ai-bubble">
          ${formatMessageContent(content)}
          ${products.length > 0 ? renderProductCards(products) : ''}
        </div>
      `;
    } else {
      messageDiv.innerHTML = `
        <div class="hearns-ai-bubble">
          ${formatMessageContent(content)}
        </div>
      `;
    }

    messagesContainer.appendChild(messageDiv);

    // Scroll to bottom
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
  }

  /**
   * Format message content (convert markdown to HTML)
   */
  function formatMessageContent(content) {
    return content
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\n/g, '<br>')
      .replace(/- (.*?)(<br>|$)/g, '<li>$1</li>')
      .replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>');
  }

  /**
   * Render product cards
   */
  function renderProductCards(products) {
    if (!products || products.length === 0) return '';

    const cards = products.map(product => `
      <div class="hearns-ai-product-card">
        <a href="${product.url}" class="hearns-ai-product-link" target="_blank">
          <img
            src="${product.image}"
            alt="${product.title}"
            class="hearns-ai-product-image"
            loading="lazy"
          />
          <div class="hearns-ai-product-info">
            <h4 class="hearns-ai-product-title">${product.title}</h4>
            ${product.vendor ? `<p class="hearns-ai-product-vendor">${product.vendor}</p>` : ''}
            <div class="hearns-ai-product-price-row">
              <span class="hearns-ai-product-price">${product.currency} $${product.price}</span>
              ${!product.inStock ? '<span class="hearns-ai-out-of-stock">Out of Stock</span>' : ''}
            </div>
          </div>
          <div class="hearns-ai-product-cta">
            <span class="hearns-ai-view-product">View Product →</span>
          </div>
        </a>
      </div>
    `).join('');

    return `<div class="hearns-ai-products">${cards}</div>`;
  }

  /**
   * Show loading indicator
   */
  function showLoadingIndicator() {
    isLoading = true;

    const messagesContainer = document.getElementById('hearns-ai-messages');
    const loadingDiv = document.createElement('div');
    loadingDiv.className = 'hearns-ai-message hearns-ai-message-assistant';
    loadingDiv.id = 'hearns-ai-loading';
    loadingDiv.innerHTML = `
      <div class="hearns-ai-avatar">${CONFIG.buttonEmoji}</div>
      <div class="hearns-ai-bubble">
        <div class="hearns-ai-loading-dots">
          <span></span><span></span><span></span>
        </div>
      </div>
    `;
    messagesContainer.appendChild(loadingDiv);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
  }

  /**
   * Hide loading indicator
   */
  function hideLoadingIndicator() {
    isLoading = false;
    const loadingDiv = document.getElementById('hearns-ai-loading');
    if (loadingDiv) {
      loadingDiv.remove();
    }
  }

  // Initialize when script loads
  init();

})();
```

---

## Step 4: Widget Styles

### ai-chat-styles.css

**File:** `/extensions/search-enhancer/assets/ai-chat-styles.css`

```css
/**
 * Hearn's Hobbies AI Search Assistant Styles
 * Theme Extension Widget
 */

/* AI Button (next to search bar) */
.hearns-ai-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  margin-left: 8px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
  white-space: nowrap;
}

.hearns-ai-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.hearns-ai-button:active {
  transform: translateY(0);
}

.hearns-ai-icon {
  font-size: 18px;
  line-height: 1;
}

/* Mobile: Hide text, show icon only */
@media (max-width: 640px) {
  .hearns-ai-button {
    padding: 10px;
    margin-left: 4px;
  }

  .hearns-ai-label {
    display: none;
  }
}

/* Modal Container */
.hearns-ai-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  display: none;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.hearns-ai-modal.hearns-ai-modal-open {
  display: flex;
}

.hearns-ai-modal-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
  animation: hearns-ai-fade-in 0.3s ease-out;
}

/* Modal Content */
.hearns-ai-modal-content {
  position: relative;
  width: 100%;
  max-width: 600px;
  max-height: 80vh;
  background: white;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  display: flex;
  flex-direction: column;
  animation: hearns-ai-slide-up 0.3s ease-out;
  z-index: 1;
}

@media (max-width: 640px) {
  .hearns-ai-modal-content {
    max-width: 100%;
    max-height: 100vh;
    border-radius: 0;
  }
}

/* Modal Header */
.hearns-ai-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid #e5e7eb;
}

.hearns-ai-modal-header h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 700;
  margin: 0;
  color: #1f2937;
}

.hearns-ai-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: transparent;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  color: #6b7280;
  transition: background 0.2s, color 0.2s;
}

.hearns-ai-close:hover {
  background: #f3f4f6;
  color: #1f2937;
}

/* Messages Container */
.hearns-ai-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: #f9fafb;
}

/* Individual Message */
.hearns-ai-message {
  display: flex;
  gap: 12px;
  max-width: 85%;
}

.hearns-ai-message-user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.hearns-ai-message-assistant {
  align-self: flex-start;
}

.hearns-ai-avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 50%;
}

.hearns-ai-bubble {
  background: white;
  padding: 12px 16px;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  font-size: 14px;
  line-height: 1.5;
  color: #374151;
}

.hearns-ai-message-user .hearns-ai-bubble {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.hearns-ai-bubble p {
  margin: 0 0 8px 0;
}

.hearns-ai-bubble p:last-child {
  margin-bottom: 0;
}

.hearns-ai-bubble ul {
  margin: 8px 0;
  padding-left: 20px;
}

.hearns-ai-bubble li {
  margin: 4px 0;
}

.hearns-ai-bubble strong {
  font-weight: 600;
  color: #1f2937;
}

.hearns-ai-message-user .hearns-ai-bubble strong {
  color: white;
}

/* Product Cards */
.hearns-ai-products {
  display: grid;
  gap: 12px;
  margin-top: 12px;
}

.hearns-ai-product-card {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  transition: box-shadow 0.2s, transform 0.2s;
}

.hearns-ai-product-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.hearns-ai-product-link {
  display: flex;
  gap: 12px;
  text-decoration: none;
  color: inherit;
  padding: 12px;
}

.hearns-ai-product-image {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 6px;
  flex-shrink: 0;
}

.hearns-ai-product-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hearns-ai-product-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
  line-height: 1.3;
}

.hearns-ai-product-vendor {
  font-size: 12px;
  color: #6b7280;
  margin: 0;
}

.hearns-ai-product-price-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: auto;
}

.hearns-ai-product-price {
  font-size: 16px;
  font-weight: 700;
  color: #10b981;
}

.hearns-ai-out-of-stock {
  font-size: 11px;
  color: #ef4444;
  background: #fee2e2;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 600;
}

.hearns-ai-product-cta {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.hearns-ai-view-product {
  font-size: 12px;
  font-weight: 600;
  color: #667eea;
  white-space: nowrap;
}

/* Input Form */
.hearns-ai-input-form {
  display: flex;
  gap: 8px;
  padding: 16px 24px;
  border-top: 1px solid #e5e7eb;
  background: white;
}

.hearns-ai-input {
  flex: 1;
  padding: 12px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.hearns-ai-input:focus {
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.hearns-ai-send {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 8px;
  color: white;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.hearns-ai-send:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.hearns-ai-send:active {
  transform: scale(0.95);
}

/* Loading Indicator */
.hearns-ai-loading-dots {
  display: flex;
  gap: 4px;
}

.hearns-ai-loading-dots span {
  width: 8px;
  height: 8px;
  background: #9ca3af;
  border-radius: 50%;
  animation: hearns-ai-bounce 1.4s infinite ease-in-out both;
}

.hearns-ai-loading-dots span:nth-child(1) {
  animation-delay: -0.32s;
}

.hearns-ai-loading-dots span:nth-child(2) {
  animation-delay: -0.16s;
}

/* Animations */
@keyframes hearns-ai-fade-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes hearns-ai-slide-up {
  from {
    transform: translateY(20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

@keyframes hearns-ai-bounce {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

/* Scrollbar Styling */
.hearns-ai-messages::-webkit-scrollbar {
  width: 6px;
}

.hearns-ai-messages::-webkit-scrollbar-track {
  background: transparent;
}

.hearns-ai-messages::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 3px;
}

.hearns-ai-messages::-webkit-scrollbar-thumb:hover {
  background: #9ca3af;
}
```

---

## Step 5: Deployment

### Build Extension

```bash
# Navigate to extension directory
cd extensions/search-enhancer

# Deploy extension to Shopify
shopify app deploy
```

### Enable Extension in Theme

1. **Go to Shopify Admin:**
   ```
   https://hearnshobbies.myshopify.com/admin/themes
   ```

2. **Click "Customize" on active theme**

3. **Open "App embeds" section** (left sidebar, bottom)

4. **Enable "AI Search Assistant"**

5. **Save**

---

## Step 6: Testing

### Test on Development Store

1. **Visit your store:**
   ```
   https://hearnshobbies.myshopify.com
   ```

2. **Look for AI button (🤖) next to search bar**

3. **Click AI button** → Modal should open

4. **Type a message:** "Show me Gundam kits"

5. **Verify:**
   - Loading indicator appears
   - API request sent to backend
   - Response appears with product cards
   - Product cards clickable

### Mobile Testing

1. **Open Chrome DevTools** → Toggle device toolbar
2. **Select iPhone 13 Pro** (or similar)
3. **Verify:**
   - Button shows icon only (no text)
   - Modal is full-screen
   - Product cards stack vertically
   - Input form accessible

### Browser Testing

- ✅ Chrome (Desktop & Mobile)
- ✅ Safari (Desktop & Mobile)
- ✅ Firefox
- ✅ Edge

---

## Step 7: Troubleshooting

### Issue: AI Button Not Appearing

**Cause:** Search bar selector not found

**Solution:**
1. Inspect your theme's search bar HTML
2. Update `findSearchBar()` selectors in `search-enhancer.js`
3. Test with your theme's specific selectors

### Issue: CORS Error When Sending Message

**Cause:** Backend CORS not configured for your shop domain

**Solution:**
1. Check backend `WebConfig.java` CORS settings
2. Add your shop domain to `allowedOrigins`
3. Redeploy backend

### Issue: Product Cards Not Rendering

**Cause:** Backend not returning `products` array

**Solution:**
1. Check backend response format
2. Verify `ChatAgentService` includes product search tool
3. Test API endpoint directly with Postman

### Issue: Modal Not Opening on Mobile

**Cause:** Z-index conflict with theme

**Solution:**
1. Increase `.hearns-ai-modal` z-index to 99999
2. Check for conflicting theme modals
3. Add `!important` if necessary

---

## Phase 2 Checklist

- [ ] Extension directory structure created
- [ ] `shopify.extension.toml` configured
- [ ] `search-enhancer.js` implemented
- [ ] `ai-chat-styles.css` created
- [ ] Extension deployed to Shopify
- [ ] Extension enabled in theme customizer
- [ ] AI button appears next to search bar
- [ ] Modal opens when button clicked
- [ ] Chat messages sent to backend API
- [ ] Product cards render correctly
- [ ] Mobile responsive design works
- [ ] Loading indicator displays during API calls
- [ ] Error handling functional
- [ ] Tested on multiple browsers

---

## Next Phase

**Phase 3: Backend Enhancements** - Optimize system prompts, add JSON formatting, and shop configuration APIs.

---

*Last Updated: 2025-10-30*
*Next: 06-PHASE3-ENHANCEMENTS.md*
