# Manual Theme Installation - Complete Code Reference

## Installation for hearnshobbies.myshopify.com

**Backend API URL**: `https://shopify-data-api-production.up.railway.app/api/shopify/chat/message`

---

## Quick Installation Steps

1. Go to: https://hearnshobbies.myshopify.com/admin/themes
2. Click **Actions** → **Edit code** on your live theme
3. Create 4 files using the code below
4. Add 2 lines to your theme.liquid file
5. Configure via Theme Customizer

**Total Time**: 10-15 minutes

---

## File 1: JavaScript Asset

**Location**: `Assets` → Add new asset → Create blank file
**Filename**: `ai-search-client.js`

```javascript
/**
 * AI Product Search Client
 * Handles communication between Shopify storefront and backend AI service
 */

class AISearchClient {
  constructor(config) {
    this.apiUrl = config.apiUrl;
    this.shopDomain = config.shopDomain;
    this.maxResults = config.maxResults || 10;
    this.messageHistory = [];
  }

  /**
   * Send a chat message to the AI assistant
   * @param {string} message - User's search query
   * @returns {Promise<Object>} - AI response with products
   */
  async sendMessage(message) {
    const requestBody = {
      message: message,
      conversationHistory: this.messageHistory,
      maxResults: this.maxResults
    };

    try {
      const response = await fetch(`${this.apiUrl}?shop=${encodeURIComponent(this.shopDomain)}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        },
        credentials: 'include',
        body: JSON.stringify(requestBody)
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.error || `HTTP ${response.status}: ${response.statusText}`);
      }

      const data = await response.json();

      // Add to message history
      this.messageHistory.push({
        role: 'user',
        content: message
      });

      this.messageHistory.push({
        role: 'assistant',
        content: data.response || data.content || ''
      });

      // Keep history manageable (last 10 messages)
      if (this.messageHistory.length > 20) {
        this.messageHistory = this.messageHistory.slice(-20);
      }

      return data;

    } catch (error) {
      console.error('AI Search Client Error:', error);
      throw error;
    }
  }

  /**
   * Clear conversation history
   */
  clearHistory() {
    this.messageHistory = [];
  }

  /**
   * Get current message history
   * @returns {Array} - Conversation history
   */
  getHistory() {
    return [...this.messageHistory];
  }
}

/**
 * AI Search Modal Controller
 * Manages the UI and interactions for the search modal
 */
class AISearchModal {
  constructor() {
    this.modal = document.getElementById('ai-search-modal');
    this.overlay = document.getElementById('ai-modal-overlay');
    this.closeBtn = document.getElementById('ai-modal-close');
    this.messagesContainer = document.getElementById('ai-chat-messages');
    this.form = document.getElementById('ai-chat-form');
    this.input = document.getElementById('ai-chat-input');
    this.submitBtn = document.getElementById('ai-chat-submit');
    this.typingIndicator = document.getElementById('ai-typing-indicator');
    this.errorMessage = document.getElementById('ai-error-message');
    this.errorText = document.getElementById('ai-error-text');

    // Initialize API client
    const apiUrl = this.modal?.dataset.apiUrl;
    const shopDomain = this.modal?.dataset.shopDomain;
    const maxResults = parseInt(this.modal?.dataset.maxResults || '10');

    if (apiUrl && shopDomain) {
      this.client = new AISearchClient({ apiUrl, shopDomain, maxResults });
    }

    this.init();
  }

  init() {
    if (!this.modal || !this.client) {
      console.error('AI Search Modal: Required elements not found');
      return;
    }

    // Close modal handlers
    this.closeBtn?.addEventListener('click', () => this.close());
    this.overlay?.addEventListener('click', () => this.close());

    // Form submission
    this.form?.addEventListener('submit', (e) => {
      e.preventDefault();
      this.handleSubmit();
    });

    // Auto-resize textarea
    this.input?.addEventListener('input', () => this.autoResizeTextarea());

    // Keyboard shortcuts
    document.addEventListener('keydown', (e) => {
      // Escape to close
      if (e.key === 'Escape' && this.isOpen()) {
        this.close();
      }

      // Ctrl/Cmd + K to open
      if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault();
        this.open();
      }
    });
  }

  isOpen() {
    return this.modal?.style.display === 'flex';
  }

  open() {
    if (this.modal) {
      this.modal.style.display = 'flex';
      setTimeout(() => this.input?.focus(), 100);
    }
  }

  close() {
    if (this.modal) {
      this.modal.style.display = 'none';
      const trigger = document.getElementById('ai-search-trigger');
      trigger?.setAttribute('aria-expanded', 'false');
    }
  }

  autoResizeTextarea() {
    if (this.input) {
      this.input.style.height = 'auto';
      this.input.style.height = Math.min(this.input.scrollHeight, 120) + 'px';
    }
  }

  async handleSubmit() {
    const message = this.input?.value.trim();

    if (!message) return;

    // Add user message to UI
    this.addMessage('user', message);

    // Clear input
    if (this.input) {
      this.input.value = '';
      this.input.style.height = 'auto';
    }

    // Show typing indicator
    this.showTyping(true);
    this.hideError();
    this.setLoading(true);

    try {
      const response = await this.client.sendMessage(message);

      this.showTyping(false);

      // Add assistant response
      this.addMessage('assistant', response.response || response.content, response);

    } catch (error) {
      this.showTyping(false);
      this.showError(error.message || 'Failed to get response. Please try again.');
      console.error('Chat error:', error);
    } finally {
      this.setLoading(false);
    }
  }

  addMessage(role, content, data = null) {
    const messageDiv = document.createElement('div');
    messageDiv.className = `ai-message ai-message-${role}`;

    const avatarDiv = document.createElement('div');
    avatarDiv.className = 'ai-message-avatar';

    if (role === 'assistant') {
      avatarDiv.innerHTML = `
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
          <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" fill="currentColor"/>
        </svg>
      `;
    } else {
      avatarDiv.innerHTML = `
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z"/>
        </svg>
      `;
    }

    const contentDiv = document.createElement('div');
    contentDiv.className = 'ai-message-content';

    // Parse content and add product cards if present
    if (role === 'assistant' && data?.products && data.products.length > 0) {
      const textP = document.createElement('p');
      textP.textContent = content;
      contentDiv.appendChild(textP);

      const productsGrid = this.createProductGrid(data.products);
      contentDiv.appendChild(productsGrid);
    } else {
      contentDiv.innerHTML = this.formatMessageContent(content);
    }

    messageDiv.appendChild(avatarDiv);
    messageDiv.appendChild(contentDiv);

    // Insert before typing indicator
    if (this.typingIndicator && this.typingIndicator.parentNode) {
      this.typingIndicator.parentNode.insertBefore(messageDiv, this.typingIndicator);
    } else {
      this.messagesContainer?.appendChild(messageDiv);
    }

    this.scrollToBottom();
  }

  createProductGrid(products) {
    const grid = document.createElement('div');
    grid.className = 'ai-product-grid';

    products.forEach(product => {
      const card = document.createElement('a');
      card.className = 'ai-product-card';
      card.href = `/products/${product.handle || product.id}`;
      card.target = '_blank';

      const img = document.createElement('img');
      img.className = 'ai-product-image';
      img.src = product.image || product.featured_image || '/placeholder.png';
      img.alt = product.title || product.name || 'Product';
      img.loading = 'lazy';

      const title = document.createElement('div');
      title.className = 'ai-product-title';
      title.textContent = product.title || product.name || 'Untitled';

      const price = document.createElement('div');
      price.className = 'ai-product-price';
      price.textContent = this.formatPrice(product.price);

      card.appendChild(img);
      card.appendChild(title);
      card.appendChild(price);

      grid.appendChild(card);
    });

    return grid;
  }

  formatPrice(price) {
    if (typeof price === 'string') {
      return price;
    }
    if (typeof price === 'number') {
      return `$${price.toFixed(2)}`;
    }
    return 'Price not available';
  }

  formatMessageContent(content) {
    // Simple markdown-like formatting
    let formatted = content
      .replace(/\n/g, '<br>')
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.*?)\*/g, '<em>$1</em>');

    return `<p>${formatted}</p>`;
  }

  showTyping(show) {
    if (this.typingIndicator) {
      this.typingIndicator.style.display = show ? 'block' : 'none';
      if (show) this.scrollToBottom();
    }
  }

  showError(message) {
    if (this.errorText && this.errorMessage) {
      this.errorText.textContent = message;
      this.errorMessage.style.display = 'block';
    }
  }

  hideError() {
    if (this.errorMessage) {
      this.errorMessage.style.display = 'none';
    }
  }

  setLoading(loading) {
    if (this.submitBtn) {
      this.submitBtn.disabled = loading;
    }
    if (this.input) {
      this.input.disabled = loading;
    }
  }

  scrollToBottom() {
    if (this.messagesContainer) {
      setTimeout(() => {
        this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
      }, 100);
    }
  }
}

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', function() {
  window.aiSearchModal = new AISearchModal();
});
```

**✅ Save this file**

---

## File 2: Assets Loader Snippet

**Location**: `Snippets` → Add new snippet
**Filename**: `ai-search-assets`

```liquid
{% comment %}
  AI Product Search Assets Loader
  Include this snippet in your theme.liquid file to load the AI search functionality

  Usage:
  {% render 'ai-search-assets' %}
{% endcomment %}

{% comment %} Load the AI Search JavaScript {% endcomment %}
<script src="{{ 'ai-search-client.js' | asset_url }}" defer></script>

{% comment %} Optional: Add keyboard shortcut hint {% endcomment %}
<style>
  .ai-keyboard-hint {
    position: fixed;
    bottom: 20px;
    left: 20px;
    padding: 8px 12px;
    background: rgba(0, 0, 0, 0.7);
    color: white;
    border-radius: 6px;
    font-size: 12px;
    font-family: monospace;
    opacity: 0;
    transition: opacity 0.3s;
    pointer-events: none;
    z-index: 9998;
  }

  .ai-keyboard-hint.show {
    opacity: 1;
  }

  .ai-kbd {
    padding: 2px 6px;
    background: rgba(255, 255, 255, 0.2);
    border-radius: 3px;
    margin: 0 2px;
  }
</style>

<div class="ai-keyboard-hint" id="ai-keyboard-hint">
  Press <span class="ai-kbd">⌘</span> + <span class="ai-kbd">K</span> to search
</div>

<script>
  // Show keyboard hint on first visit
  document.addEventListener('DOMContentLoaded', function() {
    const hint = document.getElementById('ai-keyboard-hint');
    const hasSeenHint = localStorage.getItem('ai-search-hint-seen');

    if (!hasSeenHint && hint) {
      setTimeout(() => {
        hint.classList.add('show');
        setTimeout(() => {
          hint.classList.remove('show');
          localStorage.setItem('ai-search-hint-seen', 'true');
        }, 3000);
      }, 2000);
    }
  });
</script>
```

**✅ Save this snippet**

---

## File 3: Search Bar Section

**Location**: `Sections` → Add new section
**Filename**: `ai-search-bar`

```liquid
{% comment %}
  AI Product Search Bar Block
  Renders a search input that opens the AI chat modal
{% endcomment %}

<div
  class="ai-search-bar-container"
  data-position="{{ block.settings.position }}"
  data-auto-focus="{{ block.settings.auto_focus }}"
>
  <div class="ai-search-bar-wrapper">
    <button
      type="button"
      class="ai-search-trigger"
      id="ai-search-trigger"
      aria-label="Open AI Product Search Assistant"
      aria-expanded="false"
      aria-controls="ai-search-modal"
    >
      <svg class="ai-search-icon" width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M9 17A8 8 0 1 0 9 1a8 8 0 0 0 0 16zM19 19l-4.35-4.35" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      <span class="ai-search-text">Ask our AI assistant to find products...</span>
      <svg class="ai-sparkle-icon" width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M8 1v14M1 8h14M3.5 3.5l9 9M12.5 3.5l-9 9" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
      </svg>
    </button>
  </div>
</div>

<style>
  .ai-search-bar-container {
    width: 100%;
    max-width: 600px;
    margin: 0 auto;
  }

  .ai-search-bar-container[data-position="header"] {
    margin: 1rem auto;
  }

  .ai-search-bar-container[data-position="fixed-bottom"] {
    position: fixed;
    bottom: 20px;
    right: 20px;
    left: 20px;
    z-index: 999;
    max-width: 400px;
    margin: 0 auto;
  }

  .ai-search-bar-wrapper {
    position: relative;
  }

  .ai-search-trigger {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 14px 20px;
    background: white;
    border: 2px solid {{ block.settings.primary_color | default: '#4A90E2' }};
    border-radius: 50px;
    color: {{ block.settings.text_color | default: '#333333' }};
    font-size: 16px;
    cursor: pointer;
    transition: all 0.3s ease;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  }

  .ai-search-trigger:hover {
    background: {{ block.settings.primary_color | default: '#4A90E2' }};
    color: white;
    box-shadow: 0 4px 12px rgba(74, 144, 226, 0.3);
    transform: translateY(-2px);
  }

  .ai-search-trigger:focus {
    outline: 2px solid {{ block.settings.primary_color | default: '#4A90E2' }};
    outline-offset: 2px;
  }

  .ai-search-icon {
    flex-shrink: 0;
    color: {{ block.settings.primary_color | default: '#4A90E2' }};
  }

  .ai-search-trigger:hover .ai-search-icon {
    color: white;
  }

  .ai-search-text {
    flex: 1;
    text-align: left;
    font-weight: 400;
  }

  .ai-sparkle-icon {
    flex-shrink: 0;
    color: {{ block.settings.primary_color | default: '#4A90E2' }};
    animation: sparkle 2s ease-in-out infinite;
  }

  .ai-search-trigger:hover .ai-sparkle-icon {
    color: white;
  }

  @keyframes sparkle {
    0%, 100% { opacity: 1; transform: scale(1); }
    50% { opacity: 0.6; transform: scale(1.1); }
  }

  /* Mobile responsive */
  @media (max-width: 768px) {
    .ai-search-bar-container[data-position="fixed-bottom"] {
      max-width: calc(100% - 40px);
    }

    .ai-search-trigger {
      padding: 12px 16px;
      font-size: 14px;
    }

    .ai-search-text {
      font-size: 14px;
    }
  }
</style>

<script>
  document.addEventListener('DOMContentLoaded', function() {
    const trigger = document.getElementById('ai-search-trigger');
    const modal = document.getElementById('ai-search-modal');

    if (trigger && modal) {
      trigger.addEventListener('click', function() {
        modal.style.display = 'flex';
        trigger.setAttribute('aria-expanded', 'true');

        // Focus on input when modal opens
        const input = modal.querySelector('.ai-chat-input');
        if (input) {
          setTimeout(() => input.focus(), 100);
        }
      });
    }

    // Auto-focus if enabled
    const container = document.querySelector('.ai-search-bar-container');
    if (container && container.dataset.autoFocus === 'true') {
      setTimeout(() => trigger?.focus(), 500);
    }
  });
</script>

{% schema %}
{
  "name": "AI Search Bar",
  "target": "section",
  "settings": [
    {
      "type": "color",
      "id": "primary_color",
      "label": "Primary Color",
      "default": "#4A90E2"
    },
    {
      "type": "color",
      "id": "text_color",
      "label": "Text Color",
      "default": "#333333"
    },
    {
      "type": "select",
      "id": "position",
      "label": "Position",
      "options": [
        {
          "value": "header",
          "label": "Header"
        },
        {
          "value": "fixed-bottom",
          "label": "Fixed Bottom"
        },
        {
          "value": "inline",
          "label": "Inline"
        }
      ],
      "default": "header"
    },
    {
      "type": "checkbox",
      "id": "auto_focus",
      "label": "Auto-focus on page load",
      "default": false
    }
  ]
}
{% endschema %}
```

**✅ Save this section**

---

## File 4: Search Modal Section

**Location**: `Sections` → Add new section
**Filename**: `ai-search-modal`

```liquid
{% comment %}
  AI Product Search Modal Block
  Full-screen modal with chat interface and message history
{% endcomment %}

<div
  id="ai-search-modal"
  class="ai-search-modal"
  role="dialog"
  aria-modal="true"
  aria-labelledby="ai-modal-title"
  style="display: none;"
  data-api-url="{{ section.settings.api_url }}"
  data-shop-domain="{{ shop.permanent_domain }}"
  data-max-results="{{ section.settings.max_results }}"
>
  <div class="ai-modal-overlay" id="ai-modal-overlay"></div>

  <div class="ai-modal-content">
    <!-- Modal Header -->
    <div class="ai-modal-header">
      <div class="ai-modal-title-wrapper">
        <svg class="ai-assistant-icon" width="24" height="24" viewBox="0 0 24 24" fill="none">
          <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" fill="{{ section.settings.primary_color }}" stroke="{{ section.settings.primary_color }}" stroke-width="2"/>
        </svg>
        <h2 id="ai-modal-title" class="ai-modal-title">AI Product Assistant</h2>
      </div>
      <button
        type="button"
        class="ai-modal-close"
        id="ai-modal-close"
        aria-label="Close search modal"
      >
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor">
          <path d="M18 6L6 18M6 6l12 12" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </button>
    </div>

    <!-- Chat Messages Container -->
    <div class="ai-chat-messages" id="ai-chat-messages">
      <div class="ai-welcome-message">
        <div class="ai-message ai-message-assistant">
          <div class="ai-message-avatar">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
              <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" fill="currentColor"/>
            </svg>
          </div>
          <div class="ai-message-content">
            <p>Hi! I'm your AI shopping assistant for {{ shop.name }}. Ask me to help you find products like:</p>
            <ul class="ai-suggestion-list">
              <li>"Show me Gundam model kits under $50"</li>
              <li>"I need hobby paints for plastic models"</li>
              <li>"What tools do I need for scale modeling?"</li>
            </ul>
            <p class="ai-tip">💡 Be as specific as you'd like - I can search by brand, price, category, or even project type!</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Typing Indicator -->
    <div class="ai-typing-indicator" id="ai-typing-indicator" style="display: none;">
      <div class="ai-message ai-message-assistant">
        <div class="ai-message-avatar">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" fill="currentColor"/>
          </svg>
        </div>
        <div class="ai-message-content">
          <div class="ai-typing-dots">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>
      </div>
    </div>

    <!-- Error Message -->
    <div class="ai-error-message" id="ai-error-message" style="display: none;">
      <div class="ai-error-content">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor">
          <circle cx="12" cy="12" r="10" stroke-width="2"/>
          <path d="M12 8v4M12 16h.01" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <span id="ai-error-text"></span>
      </div>
    </div>

    <!-- Chat Input -->
    <div class="ai-chat-input-container">
      <form id="ai-chat-form" class="ai-chat-form">
        <textarea
          id="ai-chat-input"
          class="ai-chat-input"
          placeholder="Ask me anything about our products..."
          rows="1"
          aria-label="Product search query"
          required
        ></textarea>
        <button
          type="submit"
          class="ai-chat-submit"
          id="ai-chat-submit"
          aria-label="Send message"
        >
          <svg class="ai-send-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor">
            <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
      </form>
      <div class="ai-chat-footer">
        <span class="ai-powered-by">Powered by Claude AI</span>
      </div>
    </div>
  </div>
</div>

<style>
  /* Modal Container */
  .ai-search-modal {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 9999;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 20px;
  }

  .ai-modal-overlay {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.5);
    backdrop-filter: blur(4px);
  }

  .ai-modal-content {
    position: relative;
    width: 100%;
    max-width: 800px;
    height: 90vh;
    max-height: 800px;
    background: white;
    border-radius: 16px;
    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
    display: flex;
    flex-direction: column;
    overflow: hidden;
    animation: modalSlideIn 0.3s ease-out;
  }

  @keyframes modalSlideIn {
    from {
      opacity: 0;
      transform: translateY(20px) scale(0.95);
    }
    to {
      opacity: 1;
      transform: translateY(0) scale(1);
    }
  }

  /* Modal Header */
  .ai-modal-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 20px 24px;
    border-bottom: 1px solid #e5e7eb;
    background: linear-gradient(135deg, {{ section.settings.primary_color | default: '#4A90E2' }}15 0%, white 100%);
  }

  .ai-modal-title-wrapper {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .ai-assistant-icon {
    animation: sparkle 2s ease-in-out infinite;
  }

  .ai-modal-title {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
    color: {{ section.settings.text_color | default: '#333333' }};
  }

  .ai-modal-close {
    padding: 8px;
    background: transparent;
    border: none;
    border-radius: 8px;
    cursor: pointer;
    color: #6b7280;
    transition: all 0.2s;
  }

  .ai-modal-close:hover {
    background: #f3f4f6;
    color: {{ section.settings.text_color | default: '#333333' }};
  }

  /* Chat Messages */
  .ai-chat-messages {
    flex: 1;
    overflow-y: auto;
    padding: 24px;
    background: #f9fafb;
    scroll-behavior: smooth;
  }

  .ai-message {
    display: flex;
    gap: 12px;
    margin-bottom: 20px;
    animation: messageSlideIn 0.3s ease-out;
  }

  @keyframes messageSlideIn {
    from {
      opacity: 0;
      transform: translateY(10px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }

  .ai-message-avatar {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  .ai-message-assistant .ai-message-avatar {
    background: {{ section.settings.primary_color | default: '#4A90E2' }};
    color: white;
  }

  .ai-message-user .ai-message-avatar {
    background: #6b7280;
    color: white;
  }

  .ai-message-content {
    flex: 1;
    padding: 12px 16px;
    border-radius: 12px;
    line-height: 1.6;
  }

  .ai-message-assistant .ai-message-content {
    background: white;
    border: 1px solid #e5e7eb;
  }

  .ai-message-user .ai-message-content {
    background: {{ section.settings.primary_color | default: '#4A90E2' }};
    color: white;
    margin-left: auto;
  }

  .ai-message-user {
    flex-direction: row-reverse;
  }

  .ai-suggestion-list {
    margin: 12px 0;
    padding-left: 20px;
  }

  .ai-suggestion-list li {
    margin-bottom: 8px;
    color: #6b7280;
    font-style: italic;
  }

  .ai-tip {
    margin-top: 12px;
    font-size: 14px;
    color: #6b7280;
  }

  /* Product Cards */
  .ai-product-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 16px;
    margin-top: 16px;
  }

  .ai-product-card {
    background: #f9fafb;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    padding: 12px;
    transition: all 0.2s;
    text-decoration: none;
    color: inherit;
    display: block;
  }

  .ai-product-card:hover {
    border-color: {{ section.settings.primary_color | default: '#4A90E2' }};
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }

  .ai-product-image {
    width: 100%;
    aspect-ratio: 1;
    object-fit: cover;
    border-radius: 6px;
    margin-bottom: 8px;
  }

  .ai-product-title {
    font-size: 14px;
    font-weight: 600;
    margin-bottom: 4px;
    color: {{ section.settings.text_color | default: '#333333' }};
  }

  .ai-product-price {
    font-size: 16px;
    font-weight: 700;
    color: {{ section.settings.primary_color | default: '#4A90E2' }};
  }

  /* Typing Indicator */
  .ai-typing-dots {
    display: flex;
    gap: 4px;
    padding: 8px 0;
  }

  .ai-typing-dots span {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: {{ section.settings.primary_color | default: '#4A90E2' }};
    animation: typing 1.4s ease-in-out infinite;
  }

  .ai-typing-dots span:nth-child(2) {
    animation-delay: 0.2s;
  }

  .ai-typing-dots span:nth-child(3) {
    animation-delay: 0.4s;
  }

  @keyframes typing {
    0%, 60%, 100% { transform: translateY(0); opacity: 0.7; }
    30% { transform: translateY(-10px); opacity: 1; }
  }

  /* Error Message */
  .ai-error-message {
    padding: 12px 24px;
    background: #fef2f2;
    border-top: 1px solid #fecaca;
  }

  .ai-error-content {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #dc2626;
    font-size: 14px;
  }

  /* Chat Input */
  .ai-chat-input-container {
    border-top: 1px solid #e5e7eb;
    background: white;
  }

  .ai-chat-form {
    display: flex;
    gap: 12px;
    padding: 16px 24px;
  }

  .ai-chat-input {
    flex: 1;
    padding: 12px 16px;
    border: 2px solid #e5e7eb;
    border-radius: 12px;
    font-size: 15px;
    font-family: inherit;
    resize: none;
    min-height: 44px;
    max-height: 120px;
    transition: border-color 0.2s;
  }

  .ai-chat-input:focus {
    outline: none;
    border-color: {{ section.settings.primary_color | default: '#4A90E2' }};
  }

  .ai-chat-submit {
    width: 44px;
    height: 44px;
    padding: 0;
    background: {{ section.settings.primary_color | default: '#4A90E2' }};
    border: none;
    border-radius: 12px;
    color: white;
    cursor: pointer;
    transition: all 0.2s;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  .ai-chat-submit:hover:not(:disabled) {
    background: {{ section.settings.primary_color | default: '#3A7BC2' }};
    transform: scale(1.05);
  }

  .ai-chat-submit:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  .ai-chat-footer {
    padding: 0 24px 16px;
    text-align: center;
  }

  .ai-powered-by {
    font-size: 12px;
    color: #9ca3af;
  }

  /* Mobile Responsive */
  @media (max-width: 768px) {
    .ai-search-modal {
      padding: 0;
    }

    .ai-modal-content {
      max-width: 100%;
      height: 100vh;
      max-height: 100vh;
      border-radius: 0;
    }

    .ai-product-grid {
      grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
      gap: 12px;
    }
  }

  /* Scrollbar Styling */
  .ai-chat-messages::-webkit-scrollbar {
    width: 8px;
  }

  .ai-chat-messages::-webkit-scrollbar-track {
    background: #f3f4f6;
  }

  .ai-chat-messages::-webkit-scrollbar-thumb {
    background: #d1d5db;
    border-radius: 4px;
  }

  .ai-chat-messages::-webkit-scrollbar-thumb:hover {
    background: #9ca3af;
  }
</style>

{% schema %}
{
  "name": "AI Search Modal",
  "target": "section",
  "settings": [
    {
      "type": "color",
      "id": "primary_color",
      "label": "Primary Color",
      "default": "#4A90E2"
    },
    {
      "type": "color",
      "id": "text_color",
      "label": "Text Color",
      "default": "#333333"
    },
    {
      "type": "text",
      "id": "api_url",
      "label": "Backend API URL",
      "default": "https://shopify-data-api-production.up.railway.app/api/shopify/chat/message",
      "info": "Your backend API endpoint"
    },
    {
      "type": "range",
      "id": "max_results",
      "label": "Maximum Results",
      "min": 3,
      "max": 20,
      "step": 1,
      "default": 10
    }
  ]
}
{% endschema %}
```

**✅ Save this section**

---

## File 5: Update theme.liquid

**Location**: `Layout` → `theme.liquid`

**Find the closing `</body>` tag** (near the bottom of the file)

**Add these 2 lines BEFORE the `</body>` tag:**

```liquid
{% render 'ai-search-assets' %}
{% section 'ai-search-modal' %}
```

**Example of where to place it:**

```liquid
  ... existing theme code ...

  {% render 'ai-search-assets' %}
  {% section 'ai-search-modal' %}
</body>
</html>
```

**✅ Save theme.liquid**

---

## Configuration via Theme Customizer

Now that all files are uploaded, let's configure the search interface:

### Step 1: Access Theme Customizer

1. Go to: https://hearnshobbies.myshopify.com/admin/themes
2. Click **Customize** on your live theme

### Step 2: Add Search Bar to Homepage

1. Navigate to your **Homepage**
2. Click **Add section** (usually at the top or in header area)
3. Scroll down and find **AI Search Bar**
4. Click to add it
5. Configure settings in the right panel:
   - **Primary Color**: `#4A90E2` (or your brand color)
   - **Text Color**: `#333333`
   - **Position**: Choose **Header** or **Fixed Bottom**
   - **Auto-focus**: Leave unchecked (unless you want it auto-focused)

### Step 3: Configure Modal Settings

1. While still in Theme Customizer, look for **Theme settings** (gear icon at bottom left)
2. Or find **Sections** → **AI Search Modal**
3. Configure:
   - **Backend API URL**: `https://shopify-data-api-production.up.railway.app/api/shopify/chat/message`
   - **Maximum Results**: `10`
   - **Primary Color**: `#4A90E2` (match your search bar)
   - **Text Color**: `#333333`

### Step 4: Save Everything

1. Click **Save** (top right)
2. Close Theme Customizer

---

## Testing

### Test 1: Visual Check

1. Visit your storefront: https://hearnshobbies.com
2. You should see the AI search bar
3. Click it - the modal should open

### Test 2: Search Query

1. Open the AI search modal
2. Type: "Show me Gundam model kits under $50"
3. Press Enter or click Send
4. You should see:
   - Typing indicator (3 animated dots)
   - AI response with product recommendations
   - Product cards (if products found)

### Test 3: Keyboard Shortcut

1. Press `Cmd + K` (Mac) or `Ctrl + K` (Windows)
2. Modal should open

---

## Troubleshooting

### Issue: Modal doesn't open

**Check:**
1. Browser console for errors (F12 → Console tab)
2. Verify `ai-search-assets` snippet is loaded in theme.liquid
3. Verify `ai-search-modal` section is added to theme.liquid

### Issue: No response from AI

**Check:**
1. API URL is correct in modal settings
2. Railway backend is running: https://shopify-data-api-production.up.railway.app/api/shopify/chat/message
3. CORS settings allow your domain
4. Check Railway logs for errors

### Issue: Products not showing

**Check:**
1. Products exist in Shopify catalog
2. AI-Connector app has `read_products` permission
3. Backend can access Shopify API (check Railway logs)

---

## Summary

✅ **4 Files Created:**
1. `ai-search-client.js` (Assets)
2. `ai-search-assets` (Snippets)
3. `ai-search-bar` (Sections)
4. `ai-search-modal` (Sections)

✅ **1 File Modified:**
5. `theme.liquid` (Layout)

✅ **Configuration:**
- API URL: `https://shopify-data-api-production.up.railway.app/api/shopify/chat/message`
- Max Results: 10
- Primary Color: #4A90E2
- Position: Header or Fixed Bottom

**Total Installation Time**: 10-15 minutes

---

## Need Help?

If you encounter any issues, check:
1. Browser console for JavaScript errors
2. Railway logs for backend errors
3. Shopify theme editor for liquid syntax errors

Good luck! 🚀
