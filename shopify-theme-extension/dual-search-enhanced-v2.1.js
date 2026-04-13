/**
 * Dual Mode Search - Fully Isolated Version
 * Version: 2.1 - Bug Fix Release
 *
 * IMPORTANT: This version is completely isolated and will NOT interfere with:
 * - Theme search forms
 * - Product variant selectors
 * - Navigation dropdowns
 * - Any other page elements
 */

(function() {
  'use strict';

  // Namespace to avoid global conflicts
  const AI_SEARCH = {
    initialized: false,
    messageInterval: null,
    timerInterval: null
  };

  // Configuration
  const CONFIG = {
    SEARCH_TIMEOUT: 30000,
    MESSAGE_INTERVAL: 3000,
    FORM_SELECTOR: '#ai-dual-search-form', // ONLY our form
    FLOATING_BTN_SELECTOR: '[data-ai-search-button]',
    MODAL_SELECTOR: '[data-ai-search-modal]'
  };

  // Category detection patterns
  const CATEGORY_PATTERNS = {
    trains: {
      keywords: ['train', 'railroad', 'railway', 'locomotive', 'caboose', 'ho scale', 'n scale', 'o scale'],
      icon: '🚂',
      name: 'model trains',
      messages: [
        'Looking through our model trains collection...',
        'Checking locomotives and rolling stock...',
        'Finding the perfect trains for your layout...',
        'Comparing track systems and accessories...'
      ]
    },
    rc: {
      keywords: ['rc', 'remote control', 'drone', 'quadcopter', 'helicopter', 'car remote'],
      icon: '🚗',
      name: 'RC vehicles',
      messages: [
        'Browsing through our RC collection...',
        'Checking batteries, controllers, and parts...',
        'Finding the perfect RC vehicle for you...',
        'Comparing speed and performance options...'
      ]
    },
    paint: {
      keywords: ['paint', 'acrylic', 'brush', 'primer', 'varnish', 'color', 'pigment'],
      icon: '🎨',
      name: 'paints and supplies',
      messages: [
        'Browsing through our paint collections...',
        'Looking at brushes, colors, and finishes...',
        'Finding the perfect paint supplies for your project...',
        'Comparing brands and quality options...'
      ]
    },
    miniatures: {
      keywords: ['miniature', 'warhammer', 'dungeons', 'dragon', 'mini', 'figurine', 'tabletop'],
      icon: '🎲',
      name: 'miniatures and gaming',
      messages: [
        'Searching through our miniatures collection...',
        'Looking at paints, tools, and accessories...',
        'Finding perfect additions to your army...',
        'Checking rulebooks and game accessories...'
      ]
    },
    models: {
      keywords: ['model kit', 'plastic model', 'aircraft', 'ship model', 'tank model', 'gundam', 'revell'],
      icon: '✈️',
      name: 'model kits',
      messages: [
        'Browsing through our model kits...',
        'Looking at aircraft, vehicles, and ships...',
        'Finding the perfect build for your skill level...',
        'Checking tools and accessories you might need...'
      ]
    },
    puzzles: {
      keywords: ['puzzle', 'jigsaw', 'brain teaser', '3d puzzle'],
      icon: '🧩',
      name: 'puzzles',
      messages: [
        'Searching through our puzzle collection...',
        'Looking at different themes and piece counts...',
        'Finding the perfect challenge for you...',
        'Checking difficulty levels and sizes...'
      ]
    },
    games: {
      keywords: ['board game', 'card game', 'strategy game', 'family game', 'game night'],
      icon: '🎮',
      name: 'games',
      messages: [
        'Browsing through our game library...',
        'Looking at strategy, family, and party games...',
        'Finding the perfect game for your group...',
        'Checking player counts and play times...'
      ]
    },
    craft: {
      keywords: ['craft', 'glue', 'scissors', 'paper', 'scrapbook', 'needle', 'thread'],
      icon: '✂️',
      name: 'craft supplies',
      messages: [
        'Searching through our craft supplies...',
        'Looking at tools and materials...',
        'Finding everything you need for your project...',
        'Checking quality and value options...'
      ]
    }
  };

  /**
   * Initialize - called once when DOM is ready
   */
  function init() {
    if (AI_SEARCH.initialized) return;

    try {
      initializeSearchToggle();
      initializeFloatingButton();
      initializeSearchSubmit();
      AI_SEARCH.initialized = true;
      console.log('✅ AI Search initialized successfully');
    } catch (error) {
      console.error('❌ AI Search initialization error:', error);
    }
  }

  /**
   * Initialize search mode toggle - ONLY for our form
   */
  function initializeSearchToggle() {
    // Only target buttons with our specific data attribute INSIDE our form
    const ourForm = document.querySelector(CONFIG.FORM_SELECTOR);
    if (!ourForm) {
      console.log('ℹ️ AI dual search form not found on this page');
      return;
    }

    const toggles = ourForm.querySelectorAll('[data-search-toggle]');

    toggles.forEach(toggle => {
      toggle.addEventListener('click', handleToggleClick);
    });

    console.log(`✅ Search toggle initialized (${toggles.length} buttons)`);
  }

  function handleToggleClick(e) {
    const button = e.currentTarget;
    const form = button.closest(CONFIG.FORM_SELECTOR);

    // Extra safety: ensure this is our form
    if (!form || form.id !== 'ai-dual-search-form') return;

    const searchModeInput = form.querySelector('[name="search_mode"]');
    const standardBtn = form.querySelector('[data-search-mode="standard"]');
    const aiBtn = form.querySelector('[data-search-mode="ai"]');

    if (!searchModeInput || !standardBtn || !aiBtn) return;

    const targetMode = button.dataset.searchMode;
    searchModeInput.value = targetMode;

    // Update button states
    standardBtn.classList.toggle('active', targetMode === 'standard');
    aiBtn.classList.toggle('active', targetMode === 'ai');
    standardBtn.setAttribute('aria-pressed', targetMode === 'standard');
    aiBtn.setAttribute('aria-pressed', targetMode === 'ai');

    // Track event
    trackEvent('search_mode_toggle', { mode: targetMode });
  }

  /**
   * Initialize floating AI button
   */
  function initializeFloatingButton() {
    const floatingBtn = document.querySelector(CONFIG.FLOATING_BTN_SELECTOR);
    if (!floatingBtn) {
      console.log('ℹ️ AI floating button not found on this page');
      return;
    }

    floatingBtn.addEventListener('click', openAISearchModal);

    // Keyboard shortcut - with safety checks
    document.addEventListener('keydown', handleKeyboardShortcut);

    console.log('✅ Floating button initialized');
  }

  function handleKeyboardShortcut(e) {
    // Only trigger on Cmd+K / Ctrl+K
    if (!((e.metaKey || e.ctrlKey) && e.key === 'k')) return;

    // Safety check: Don't interfere if user is in any input field
    const activeElement = document.activeElement;
    if (!activeElement) return;

    const isInputField = (
      activeElement.tagName === 'INPUT' ||
      activeElement.tagName === 'TEXTAREA' ||
      activeElement.tagName === 'SELECT' ||
      activeElement.isContentEditable ||
      activeElement.closest('form') !== null // Inside ANY form
    );

    if (isInputField) {
      return; // Let browser handle it normally
    }

    // Only prevent default if we're actually going to open the modal
    const modal = document.querySelector(CONFIG.MODAL_SELECTOR);
    if (modal) {
      e.preventDefault();
      e.stopPropagation();
      openAISearchModal();
    }
  }

  function openAISearchModal() {
    const modal = document.querySelector(CONFIG.MODAL_SELECTOR);
    const searchInput = document.querySelector('[data-ai-search-input]');

    if (modal) {
      modal.classList.add('active');
      modal.setAttribute('aria-hidden', 'false');

      if (searchInput) {
        setTimeout(() => searchInput.focus(), 100);
      }

      trackEvent('ai_search_modal_opened', { trigger: 'button' });
    }
  }

  /**
   * Initialize search submit handler - ONLY for our specific form
   */
  function initializeSearchSubmit() {
    // CRITICAL: Only target OUR specific form by ID
    const ourForm = document.querySelector(CONFIG.FORM_SELECTOR);

    if (!ourForm) {
      console.log('ℹ️ AI dual search form not found - search submit not initialized');
      return;
    }

    // Only attach to OUR form
    ourForm.addEventListener('submit', handleSearchSubmit);
    console.log('✅ Search submit handler attached to #ai-dual-search-form ONLY');
  }

  function handleSearchSubmit(e) {
    const form = e.currentTarget;

    // Triple safety check: ensure this is our form
    if (form.id !== 'ai-dual-search-form') {
      console.log('⚠️ Not our form, ignoring');
      return;
    }

    const searchModeInput = form.querySelector('[name="search_mode"]');
    const searchInput = form.querySelector('#ai-search-input');

    // Safety: only proceed if all our elements exist
    if (!searchModeInput || !searchInput) {
      console.log('⚠️ Missing form elements, ignoring');
      return;
    }

    // Only show loading for AI search mode
    if (searchModeInput.value !== 'ai') {
      console.log('ℹ️ Standard search mode, proceeding normally');
      return;
    }

    const searchQuery = searchInput.value.trim();
    if (!searchQuery) {
      e.preventDefault();
      return;
    }

    // Show loading overlay
    console.log('🔍 AI search triggered for:', searchQuery);
    showEnhancedLoadingOverlay(searchQuery);

    trackEvent('ai_search_submitted', {
      query: searchQuery,
      query_length: searchQuery.length
    });

    // Timeout safety
    setTimeout(() => {
      hideLoadingOverlay();
      alert('Search is taking longer than expected. Please try again or use Standard Search.');
    }, CONFIG.SEARCH_TIMEOUT);
  }

  /**
   * Detect category from search query
   */
  function detectCategory(query) {
    const lowerQuery = query.toLowerCase();

    for (const [key, category] of Object.entries(CATEGORY_PATTERNS)) {
      for (const keyword of category.keywords) {
        if (lowerQuery.includes(keyword)) {
          return category;
        }
      }
    }

    return {
      icon: '🔍',
      name: 'products',
      messages: [
        'Searching through thousands of hobby products...',
        'AI is analyzing your query for the best matches...',
        'Finding the perfect products just for you...',
        'Comparing prices and availability...'
      ]
    };
  }

  /**
   * Generate personalized progress messages
   */
  function generateProgressMessages(query, category) {
    return [
      {
        time: 0,
        text: `Searching for "<strong>${escapeHtml(query)}</strong>" across our catalog...`,
        icon: '🔍'
      },
      {
        time: 3,
        text: `${category.icon} ${category.messages[0]}`,
        icon: category.icon
      },
      {
        time: 6,
        text: `${category.icon} ${category.messages[1] || 'Finding the best matches for you...'}`,
        icon: category.icon
      },
      {
        time: 9,
        text: `✨ ${category.messages[2] || 'Camilla is comparing options...'}`,
        icon: '✨'
      },
      {
        time: 12,
        text: `🎯 Almost there! Camilla is finalizing your results...`,
        icon: '🎯'
      },
      {
        time: 15,
        text: `⏳ Just a few more seconds...`,
        icon: '⏳'
      },
      {
        time: 18,
        text: `${category.icon} ${category.messages[3] || 'Ranking products by relevance...'}`,
        icon: category.icon
      },
      {
        time: 21,
        text: `📊 Personalizing results based on your search...`,
        icon: '📊'
      },
      {
        time: 24,
        text: `💎 Selecting the best quality options...`,
        icon: '💎'
      },
      {
        time: 27,
        text: `⚡ Wrapping up - thanks for your patience!`,
        icon: '⚡'
      }
    ];
  }

  /**
   * Show loading overlay
   */
  function showEnhancedLoadingOverlay(query) {
    hideLoadingOverlay(); // Clean up any existing overlay

    const category = detectCategory(query);
    const progressMessages = generateProgressMessages(query, category);

    const overlay = document.createElement('div');
    overlay.className = 'dual-search-loading-overlay';
    overlay.setAttribute('role', 'alert');
    overlay.setAttribute('aria-live', 'polite');
    overlay.id = 'ai-search-loading';

    const content = document.createElement('div');
    content.className = 'loading-content';

    const spinner = document.createElement('div');
    spinner.className = 'loading-spinner';
    spinner.innerHTML = '<div class="spinner-ring"></div>';

    const messageContainer = document.createElement('div');
    messageContainer.className = 'loading-message';
    messageContainer.id = 'loading-message-text';

    const timerDisplay = document.createElement('div');
    timerDisplay.className = 'loading-timer';
    timerDisplay.id = 'loading-timer';
    timerDisplay.textContent = 'Elapsed: 0s';

    content.appendChild(spinner);
    content.appendChild(messageContainer);
    content.appendChild(timerDisplay);
    overlay.appendChild(content);
    document.body.appendChild(overlay);

    startProgressMessages(progressMessages);
    startTimer();

    // Prevent body scroll
    document.body.style.overflow = 'hidden';
  }

  /**
   * Cycle through progress messages
   */
  function startProgressMessages(messages) {
    let currentIndex = 0;
    const messageElement = document.getElementById('loading-message-text');
    if (!messageElement) return;

    updateMessage(messages[0]);

    AI_SEARCH.messageInterval = setInterval(() => {
      currentIndex++;
      if (currentIndex >= messages.length) {
        currentIndex = messages.length - 1;
      }
      updateMessage(messages[currentIndex]);
    }, CONFIG.MESSAGE_INTERVAL);
  }

  function updateMessage(message) {
    const messageElement = document.getElementById('loading-message-text');
    if (!messageElement || !message) return;

    messageElement.style.opacity = '0';

    setTimeout(() => {
      messageElement.innerHTML = `
        <span class="message-icon">${message.icon}</span>
        <span class="message-text">${message.text}</span>
      `;
      messageElement.style.opacity = '1';
    }, 300);
  }

  /**
   * Start timer
   */
  function startTimer() {
    let seconds = 0;
    const timerElement = document.getElementById('loading-timer');
    if (!timerElement) return;

    AI_SEARCH.timerInterval = setInterval(() => {
      seconds++;
      timerElement.textContent = `Elapsed: ${seconds}s`;

      if (seconds > 20) {
        timerElement.style.color = '#4caf50'; // Green
      } else if (seconds > 10) {
        timerElement.style.color = '#ff9800'; // Orange
      }
    }, 1000);
  }

  /**
   * Hide loading overlay and cleanup
   */
  function hideLoadingOverlay() {
    const overlay = document.getElementById('ai-search-loading');
    if (overlay) {
      overlay.remove();
    }

    if (AI_SEARCH.messageInterval) {
      clearInterval(AI_SEARCH.messageInterval);
      AI_SEARCH.messageInterval = null;
    }

    if (AI_SEARCH.timerInterval) {
      clearInterval(AI_SEARCH.timerInterval);
      AI_SEARCH.timerInterval = null;
    }

    document.body.style.overflow = '';
  }

  /**
   * Track analytics events
   */
  function trackEvent(eventName, eventData) {
    try {
      if (window.gtag) {
        window.gtag('event', eventName, eventData);
      }

      if (window.dataLayer) {
        window.dataLayer.push({
          event: eventName,
          ...eventData
        });
      }

      if (window.ShopifyAnalytics) {
        window.ShopifyAnalytics.lib.track(eventName, eventData);
      }
    } catch (error) {
      console.error('Analytics tracking error:', error);
    }
  }

  /**
   * Escape HTML to prevent XSS
   */
  function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  // Initialize when DOM is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  // Expose cleanup for debugging
  window.AI_SEARCH_CLEANUP = hideLoadingOverlay;

})();
