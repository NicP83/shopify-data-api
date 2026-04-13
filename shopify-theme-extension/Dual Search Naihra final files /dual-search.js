/**
 * Dual Mode Search - Enhanced with Personalized Progress Messages
 * Handles AI search loading with engaging, search-specific progress updates
 * Version: 2.0
 */

(function() {
  'use strict';

  // Configuration
  const SEARCH_TIMEOUT = 30000; // 30 seconds
  const MESSAGE_INTERVAL = 3000; // Change message every 3 seconds

  // Category detection patterns for hobby products
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

  // Initialize on DOM ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  function init() {
    initializeSearchToggle();
    initializeFloatingButton();
    initializeSearchSubmit();
  }

  /**
   * Initialize search mode toggle functionality
   */
  function initializeSearchToggle() {
    const toggles = document.querySelectorAll('[data-search-toggle]');

    toggles.forEach(toggle => {
      toggle.addEventListener('click', handleToggleClick);
    });
  }

  function handleToggleClick(e) {
    const button = e.currentTarget;
    const form = button.closest('form');
    if (!form) return;

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

    // Track toggle event
    trackEvent('search_mode_toggle', { mode: targetMode });
  }

  /**
   * Initialize floating AI button functionality
   */
  function initializeFloatingButton() {
    const floatingBtn = document.querySelector('[data-ai-search-button]');
    if (!floatingBtn) return;

    floatingBtn.addEventListener('click', openAISearchModal);

    // Keyboard shortcut (Cmd+K / Ctrl+K)
    document.addEventListener('keydown', function(e) {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        openAISearchModal();
      }
    });
  }

  function openAISearchModal() {
    const modal = document.querySelector('[data-ai-search-modal]');
    const searchInput = document.querySelector('[data-ai-search-input]');

    if (modal) {
      modal.classList.add('active');
      modal.setAttribute('aria-hidden', 'false');

      if (searchInput) {
        searchInput.focus();
      }

      // Track modal open
      trackEvent('ai_search_modal_opened', { trigger: 'floating_button' });
    }
  }

  /**
   * Initialize search form submit with loading overlay
   */
  function initializeSearchSubmit() {
    // Only bind to the dual search form if it exists.
    // This prevents the script from interfering with other header/theme search forms.
    const dualForm = document.getElementById('dual-search-form');
    if (dualForm) {
      dualForm.addEventListener('submit', handleSearchSubmit);
      return;
    }

    // Fallback: if you are using the self-contained non-form snippet (ai-dual-search-inner),
    // do not attach to global search forms. Nothing to do.
  }


  function handleSearchSubmit(e) {
    const form = e.currentTarget;
    const searchModeInput = form.querySelector('[name="search_mode"]');
    const searchInput = form.querySelector('input[type="search"], input[name="q"]');

    // Only show loading for AI search mode
    if (!searchModeInput || searchModeInput.value !== 'ai') {
      return; // Let standard search proceed normally
    }

    const searchQuery = searchInput ? searchInput.value.trim() : '';
    if (!searchQuery) {
      e.preventDefault();
      return;
    }

    // Show enhanced loading overlay with personalized messages
    showEnhancedLoadingOverlay(searchQuery);

    // Track AI search
    trackEvent('ai_search_submitted', {
      query: searchQuery,
      query_length: searchQuery.length
    });

    // Set timeout as safety measure
    setTimeout(() => {
      hideLoadingOverlay();
      alert('Search is taking longer than expected. Please try again or use Standard Search.');
    }, SEARCH_TIMEOUT);
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

    // Default fallback category
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
   * Generate personalized progress messages based on search query
   */
  function generateProgressMessages(query, category) {
    const messages = [
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

    return messages;
  }

  /**
   * Show enhanced loading overlay with progress messages and timer
   */
  function showEnhancedLoadingOverlay(query) {
    // Remove existing overlay if any
    hideLoadingOverlay();

    // Detect category for personalized messages
    const category = detectCategory(query);
    const progressMessages = generateProgressMessages(query, category);

    // Create overlay
    const overlay = document.createElement('div');
    overlay.className = 'dual-search-loading-overlay';
    overlay.setAttribute('role', 'alert');
    overlay.setAttribute('aria-live', 'polite');
    overlay.id = 'ai-search-loading';

    // Create content container
    const content = document.createElement('div');
    content.className = 'loading-content';

    // Create animated spinner
    const spinner = document.createElement('div');
    spinner.className = 'loading-spinner';
    spinner.innerHTML = '<div class="spinner-ring"></div>';

    // Create message container
    const messageContainer = document.createElement('div');
    messageContainer.className = 'loading-message';
    messageContainer.id = 'loading-message-text';

    // Create timer display
    const timerDisplay = document.createElement('div');
    timerDisplay.className = 'loading-timer';
    timerDisplay.id = 'loading-timer';
    timerDisplay.textContent = 'Elapsed: 0s';

    // Assemble overlay
    content.appendChild(spinner);
    content.appendChild(messageContainer);
    content.appendChild(timerDisplay);
    overlay.appendChild(content);
    document.body.appendChild(overlay);

    // Start progress messages and timer
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

    // Show first message immediately
    updateMessage(messages[0]);

    // Store interval ID for cleanup
    window.aiSearchMessageInterval = setInterval(() => {
      currentIndex++;
      if (currentIndex >= messages.length) {
        currentIndex = messages.length - 1; // Stay on last message
      }
      updateMessage(messages[currentIndex]);
    }, MESSAGE_INTERVAL);
  }

  function updateMessage(message) {
    const messageElement = document.getElementById('loading-message-text');
    if (!messageElement || !message) return;

    // Fade out
    messageElement.style.opacity = '0';

    setTimeout(() => {
      messageElement.innerHTML = `
        <span class="message-icon">${message.icon}</span>
        <span class="message-text">${message.text}</span>
      `;
      // Fade in
      messageElement.style.opacity = '1';
    }, 300);
  }

  /**
   * Start elapsed time timer
   */
  function startTimer() {
    let seconds = 0;
    const timerElement = document.getElementById('loading-timer');
    if (!timerElement) return;

    window.aiSearchTimerInterval = setInterval(() => {
      seconds++;
      timerElement.textContent = `Elapsed: ${seconds}s`;

      // Change color to show progress
      if (seconds > 20) {
        timerElement.style.color = '#4caf50'; // Green - Almost ready!
      } else if (seconds > 10) {
        timerElement.style.color = '#ff9800'; // Orange - Still working
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

    // Clear intervals
    if (window.aiSearchMessageInterval) {
      clearInterval(window.aiSearchMessageInterval);
      window.aiSearchMessageInterval = null;
    }
    if (window.aiSearchTimerInterval) {
      clearInterval(window.aiSearchTimerInterval);
      window.aiSearchTimerInterval = null;
    }

    // Restore body scroll
    document.body.style.overflow = '';
  }

  /**
   * Track analytics events
   */
  function trackEvent(eventName, eventData) {
    // Google Analytics (GA4)
    if (window.gtag) {
      window.gtag('event', eventName, eventData);
    }

    // Google Tag Manager
    if (window.dataLayer) {
      window.dataLayer.push({
        event: eventName,
        ...eventData
      });
    }

    // Shopify Analytics
    if (window.ShopifyAnalytics) {
      window.ShopifyAnalytics.lib.track(eventName, eventData);
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

  // Expose cleanup function globally (for debugging)
  window.hideAISearchLoading = hideLoadingOverlay;

})();