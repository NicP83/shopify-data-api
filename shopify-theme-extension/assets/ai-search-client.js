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

    // Initialize session tracking
    this.sessionId = this.getOrCreateSessionId();
    this.userIdentifier = this.getOrCreateUserIdentifier();

    console.log('AI Search Client initialized with session:', this.sessionId);
  }

  /**
   * Get or create persistent session ID
   * Session ID persists across page refreshes but not across browser sessions
   * @returns {string} - Session UUID
   */
  getOrCreateSessionId() {
    const storageKey = 'aiSearchSessionId';

    // Try to get existing session ID from sessionStorage
    let sessionId = sessionStorage.getItem(storageKey);

    if (!sessionId) {
      // Generate new session ID (simple UUID v4)
      sessionId = this.generateUUID();
      sessionStorage.setItem(storageKey, sessionId);
    }

    return sessionId;
  }

  /**
   * Get or create persistent user identifier (anonymous)
   * This identifier persists across sessions for analytics
   * @returns {string} - Anonymous user hash
   */
  getOrCreateUserIdentifier() {
    const storageKey = 'aiSearchUserId';

    // Try to get existing user ID from localStorage
    let userId = localStorage.getItem(storageKey);

    if (!userId) {
      // Generate new anonymous user ID
      userId = this.generateUUID();
      localStorage.setItem(storageKey, userId);
    }

    return userId;
  }

  /**
   * Generate a simple UUID v4
   * @returns {string} - UUID
   */
  generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
      const r = Math.random() * 16 | 0;
      const v = c === 'x' ? r : (r & 0x3 | 0x8);
      return v.toString(16);
    });
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
      maxResults: this.maxResults,
      sessionId: this.sessionId,
      userIdentifier: this.userIdentifier
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
