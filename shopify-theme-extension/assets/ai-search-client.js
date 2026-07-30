/**
 * AI Product Search Client
 * Handles communication between Shopify storefront and backend AI service
 */

class AISearchClient {
  constructor(config) {
    this.apiUrl = config.apiUrl;
    // Streaming endpoint (Server-Sent Events) sits alongside the blocking one.
    this.streamUrl = (config.apiUrl || '') + '/stream';
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
   * Stream a chat message via Server-Sent Events so the reply renders as it is
   * generated. Calls the provided callbacks as events arrive. Throws on any
   * transport/stream error so the caller can fall back to sendMessage().
   * @param {string} message
   * @param {{onStatus?:Function,onToken?:Function,onDone?:Function}} handlers
   * @returns {Promise<string>} the full assistant text
   */
  async streamMessage(message, handlers = {}) {
    const { onStatus, onToken, onDone } = handlers;
    const requestBody = {
      message: message,
      conversationHistory: this.messageHistory,
      maxResults: this.maxResults,
      sessionId: this.sessionId,
      userIdentifier: this.userIdentifier
    };

    const response = await fetch(`${this.streamUrl}?shop=${encodeURIComponent(this.shopDomain)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
      credentials: 'include',
      body: JSON.stringify(requestBody)
    });

    if (!response.ok || !response.body) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let fullText = '';
    let sawDone = false;

    const finish = () => {
      // Record history once, mirroring sendMessage()
      this.messageHistory.push({ role: 'user', content: message });
      this.messageHistory.push({ role: 'assistant', content: fullText });
      if (this.messageHistory.length > 20) {
        this.messageHistory = this.messageHistory.slice(-20);
      }
      if (onDone) onDone(fullText);
    };

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      let sep;
      while ((sep = buffer.indexOf('\n\n')) !== -1) {
        const frame = buffer.slice(0, sep);
        buffer = buffer.slice(sep + 2);
        const parsed = this.parseSSEFrame(frame);
        if (!parsed.event) continue;

        let payload = {};
        try { payload = parsed.data ? JSON.parse(parsed.data) : {}; } catch (e) { payload = {}; }

        if (parsed.event === 'token') {
          fullText += (payload.text || '');
          if (onToken) onToken(payload.text || '', fullText);
        } else if (parsed.event === 'status') {
          if (onStatus) onStatus(payload.text || '');
        } else if (parsed.event === 'done') {
          sawDone = true;
          finish();
          return fullText;
        } else if (parsed.event === 'error') {
          throw new Error(payload.message || 'Streaming error');
        }
      }
    }

    // Stream closed without an explicit done event
    if (!sawDone) finish();
    return fullText;
  }

  /**
   * Parse a single SSE frame into { event, data }.
   */
  parseSSEFrame(frame) {
    let event = null;
    const dataLines = [];
    for (const line of frame.split('\n')) {
      if (line.startsWith('event:')) {
        event = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim());
      }
    }
    return { event, data: dataLines.join('\n') };
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
    this.typingStatus = document.getElementById('ai-typing-status');
    this.errorMessage = document.getElementById('ai-error-message');
    this.errorText = document.getElementById('ai-error-text');

    // Reassurance messages shown while the assistant is working, so the
    // customer can see the system is active rather than staring at a wait.
    this.statusMessages = [
      'Searching our catalogue…',
      'Looking through the range…',
      'Checking stock and prices…',
      'Comparing the best options for you…',
      'Putting your answer together…'
    ];
    this.statusTimer = null;
    this.statusStart = 0;

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

    // Handle clicks outside modal content (on the modal container)
    this.modal?.addEventListener('click', (e) => {
      // Only close if clicking directly on the modal container (not modal content)
      if (e.target === this.modal) {
        this.close();
      }
    });

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

    // Show typing indicator (dots + rotating reassurance until the first token)
    this.showTyping(true);
    this.hideError();
    this.setLoading(true);

    // Streaming render target — created lazily on the first token.
    let streamEl = null;
    let streamedText = '';
    const ensureStreamEl = () => {
      if (!streamEl) {
        this.showTyping(false);
        streamEl = this.addStreamingAssistant();
      }
      return streamEl;
    };

    try {
      await this.client.streamMessage(message, {
        onStatus: (text) => {
          // Real backend progress. Before the first token it replaces the canned
          // rotating messages; mid-stream (between tool calls) it re-shows the
          // indicator with this status below the answer-so-far, so the customer
          // can see work is continuing rather than staring at a paused reply.
          if (text) {
            if (streamEl) this.showStatusLine(text);
            else this.setTypingStatus(text);
          }
        },
        onToken: (chunk, full) => {
          const el = ensureStreamEl();
          streamedText = full || streamedText;
          // Text is flowing again — hide the mid-stream status indicator.
          if (this.typingIndicator && this.typingIndicator.style.display !== 'none') {
            this.showTyping(false);
          }
          this.updateStreamingAssistant(el, full);
        },
        onDone: (full) => {
          const el = ensureStreamEl();
          streamedText = full || streamedText;
          this.updateStreamingAssistant(el, full);
        }
      });
    } catch (streamError) {
      console.warn('Streaming failed:', streamError);
      // If a substantial answer already streamed in, KEEP it — do not discard a
      // fully-rendered reply (e.g. a product list with cart links) only to re-ask
      // via the blocking endpoint, which returns a *different* response and makes
      // the list appear to vanish. Only fall back to blocking when little streamed.
      if (streamEl && streamedText.trim().length >= 80) {
        this.updateStreamingAssistant(streamEl, streamedText);
        // Record to history so the next turn keeps context (mirrors streamMessage.finish()).
        if (Array.isArray(this.client.messageHistory)) {
          this.client.messageHistory.push({ role: 'user', content: message });
          this.client.messageHistory.push({ role: 'assistant', content: streamedText });
          if (this.client.messageHistory.length > 20) {
            this.client.messageHistory = this.client.messageHistory.slice(-20);
          }
        }
      } else {
        // Nothing usable streamed — remove the empty bubble and fall back.
        if (streamEl && streamEl.parentNode) {
          streamEl.parentNode.removeChild(streamEl);
          streamEl = null;
        }
        this.showTyping(true);
        try {
          const response = await this.client.sendMessage(message);
          this.showTyping(false);
          this.addMessage('assistant', response.response || response.content, response);
        } catch (error) {
          this.showTyping(false);
          this.showError(error.message || 'Failed to get response. Please try again.');
          console.error('Chat error:', error);
        }
      }
    } finally {
      this.showTyping(false);
      this.setLoading(false);
    }
  }

  /**
   * Create an empty assistant message bubble to fill as tokens stream in.
   * Returns the outer message element (so it can be removed on fallback).
   */
  addStreamingAssistant() {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'ai-message ai-message-assistant';

    const avatarDiv = document.createElement('div');
    avatarDiv.className = 'ai-message-avatar';
    avatarDiv.innerHTML = `
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
        <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" fill="currentColor"/>
      </svg>
    `;

    const contentDiv = document.createElement('div');
    contentDiv.className = 'ai-message-content';
    contentDiv._streamRoot = true;

    messageDiv.appendChild(avatarDiv);
    messageDiv.appendChild(contentDiv);

    if (this.typingIndicator && this.typingIndicator.parentNode) {
      this.typingIndicator.parentNode.insertBefore(messageDiv, this.typingIndicator);
    } else {
      this.messagesContainer?.appendChild(messageDiv);
    }

    this.scrollToBottom();
    return messageDiv;
  }

  /**
   * Re-render the streaming assistant bubble with the accumulated text so far.
   */
  updateStreamingAssistant(messageEl, fullText) {
    if (!messageEl) return;
    const contentDiv = messageEl.querySelector('.ai-message-content');
    if (contentDiv) {
      contentDiv.innerHTML = this.formatMessageContent(fullText || '');
    }
    this.scrollToBottom();
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
      const textDiv = document.createElement('div');
      textDiv.innerHTML = this.formatMessageContent(content);
      contentDiv.appendChild(textDiv);

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
    // Split into lines for block-level parsing
    const lines = content.split('\n');
    let html = '';
    let inTable = false;
    let inList = false;
    let listType = 'ul';
    let tableRows = [];
    let listItems = [];

    const flushTable = () => {
      if (tableRows.length === 0) return '';
      // Filter out null separator placeholders for safety
      const validRows = tableRows.filter(r => r !== null);
      if (validRows.length === 0) { tableRows = []; return ''; }

      let t = '<table class="ai-md-table"><thead><tr>';
      validRows[0].forEach(h => { t += `<th>${this.formatInline(h.trim())}</th>`; });
      t += '</tr></thead><tbody>';
      for (let i = 1; i < validRows.length; i++) {
        t += '<tr>';
        validRows[i].forEach(cell => { t += `<td>${this.formatInline(cell.trim())}</td>`; });
        t += '</tr>';
      }
      t += '</tbody></table>';
      tableRows = [];
      return t;
    };

    const flushList = () => {
      if (listItems.length === 0) return '';
      const tag = listType === 'ol' ? 'ol' : 'ul';
      const cls = listType === 'ol' ? 'ai-md-ol' : 'ai-md-list';
      let l = `<${tag} class="${cls}">`;
      listItems.forEach(item => { l += `<li>${this.formatInline(item)}</li>`; });
      l += `</${tag}>`;
      listItems = [];
      listType = 'ul';
      return l;
    };

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];

      // Table row detection (pipes)
      if (line.trim().startsWith('|') && line.trim().endsWith('|')) {
        // Flush list if we were in one
        if (inList) { html += flushList(); inList = false; }
        // Skip separator rows like |---|---|
        if (/^\|[\s\-:|]+\|$/.test(line.trim())) {
          if (!inTable) continue; // stray separator
          tableRows.push(null); // placeholder for separator
          inTable = true;
          continue;
        }
        const cells = line.trim().slice(1, -1).split('|');
        tableRows.push(cells);
        inTable = true;
        continue;
      }

      // End of table
      if (inTable) { html += flushTable(); inTable = false; }

      // Headers
      if (line.startsWith('### ')) {
        if (inList) { html += flushList(); inList = false; }
        html += `<h4 class="ai-md-h4">${this.formatInline(line.slice(4))}</h4>`;
        continue;
      }
      if (line.startsWith('## ')) {
        if (inList) { html += flushList(); inList = false; }
        html += `<h3 class="ai-md-h3">${this.formatInline(line.slice(3))}</h3>`;
        continue;
      }

      // Horizontal rule (---, ***, ___)
      if (/^(-{3,}|\*{3,}|_{3,})$/.test(line.trim())) {
        if (inList) { html += flushList(); inList = false; }
        html += '<hr class="ai-md-hr">';
        continue;
      }

      // Ordered list items (1. 2. 3.)
      if (/^\d+\.\s+/.test(line.trim())) {
        if (inList && listType !== 'ol') { html += flushList(); }
        const itemText = line.trim().replace(/^\d+\.\s+/, '');
        listItems.push(itemText);
        listType = 'ol';
        inList = true;
        continue;
      }

      // Unordered list items
      if (/^[\-\*]\s+/.test(line.trim())) {
        if (inList && listType !== 'ul') { html += flushList(); }
        const itemText = line.trim().replace(/^[\-\*]\s+/, '');
        listItems.push(itemText);
        listType = 'ul';
        inList = true;
        continue;
      }

      // End of list
      if (inList) { html += flushList(); inList = false; }

      // Empty line → break
      if (line.trim() === '') {
        html += '<br>';
        continue;
      }

      // Regular paragraph line
      html += `<p>${this.formatInline(line)}</p>`;
    }

    // Flush any remaining block
    if (inTable) html += flushTable();
    if (inList) html += flushList();

    return html;
  }

  formatInline(text) {
    return text
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.*?)\*/g, '<em>$1</em>')
      .replace(/\[([^\]]+)\]\(([^\)]*\/cart\/[^\)]*)\)/g, '<a href="$2" target="_blank" rel="noopener" class="ai-cart-link">🛒 $1</a>')
      .replace(/\[([^\]]+)\]\(([^\)]*\/products\/[^\)]*)\)/g, '<a href="$2" target="_blank" rel="noopener" class="ai-product-link">🔍 $1</a>')
      .replace(/\[([^\]]+)\]\(([^\)]+)\)/g, '<a href="$2" target="_blank" rel="noopener" class="ai-link">$1</a>');
  }

  showTyping(show) {
    if (this.typingIndicator) {
      this.typingIndicator.style.display = show ? 'block' : 'none';
      if (show) {
        this.startTypingStatus();
        this.scrollToBottom();
      } else {
        this.stopTypingStatus();
      }
    }
  }

  /**
   * Cycle reassurance messages (with an elapsed hint after a few seconds) so a
   * multi-second wait reads as active work rather than a frozen UI.
   */
  startTypingStatus() {
    if (!this.typingStatus) return;
    this.stopTypingStatus();
    this.statusStart = Date.now();

    let index = 0;
    const render = () => {
      const elapsed = Math.round((Date.now() - this.statusStart) / 1000);
      let text = this.statusMessages[index % this.statusMessages.length];
      // After a longer wait, reassure explicitly and show elapsed seconds.
      if (elapsed >= 8) {
        text = `Still working on it — almost there… (${elapsed}s)`;
      } else if (elapsed >= 4) {
        text = `${text} (${elapsed}s)`;
      }
      this.typingStatus.textContent = text;
    };

    render();
    this.statusTimer = setInterval(() => {
      index++;
      render();
    }, 2500);
  }

  stopTypingStatus() {
    if (this.statusTimer) {
      clearInterval(this.statusTimer);
      this.statusTimer = null;
    }
    if (this.typingStatus) {
      this.typingStatus.textContent = '';
    }
  }

  /**
   * Show a specific status line (real backend progress). Delegates to showLiveStatus
   * so the message keeps a ticking elapsed hint instead of freezing — important for
   * longer phases like "Checking with our paint expert…".
   */
  setTypingStatus(text) {
    this.showLiveStatus(text, false);
  }

  /**
   * Re-show the typing indicator (dots) with a real status line during a
   * mid-stream tool phase — i.e. after some answer text has already streamed
   * in and the assistant goes back to using a tool. Rendered below the
   * answer-so-far because the streaming bubble is inserted before the indicator.
   */
  showStatusLine(text) {
    this.showLiveStatus(text, true);
  }

  /**
   * Show a real backend status line and keep it alive: append an elapsed-seconds
   * hint and escalate the wording after a few seconds, so a long tool phase
   * (e.g. delegating to a specialist agent) never looks frozen.
   */
  showLiveStatus(text, withIndicator) {
    if (this.statusTimer) {
      clearInterval(this.statusTimer);
      this.statusTimer = null;
    }
    if (withIndicator && this.typingIndicator) {
      this.typingIndicator.style.display = 'block';
    }
    if (!this.typingStatus) {
      if (withIndicator) this.scrollToBottom();
      return;
    }

    // Strip any trailing ellipsis/punctuation so we can re-suffix cleanly.
    const stem = (text || 'Working on it').replace(/[…\.\s]+$/, '');
    this.statusStart = Date.now();
    const render = () => {
      const elapsed = Math.round((Date.now() - this.statusStart) / 1000);
      if (elapsed >= 12) {
        this.typingStatus.textContent = `${stem} — almost there, thanks for your patience… (${elapsed}s)`;
      } else if (elapsed >= 4) {
        this.typingStatus.textContent = `${stem}… (${elapsed}s)`;
      } else {
        this.typingStatus.textContent = `${stem}…`;
      }
    };
    render();
    this.statusTimer = setInterval(render, 1000);
    if (withIndicator) this.scrollToBottom();
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
