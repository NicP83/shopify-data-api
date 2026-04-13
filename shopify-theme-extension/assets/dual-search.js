/**
 * Dual Search Mode - Loading Message Handler
 * Displays "Searching with Camilla... may take up to 15 seconds" message
 */

(function() {
  'use strict';

  // Loading overlay HTML
  const loadingHTML = `
    <div class="camilla-loading-overlay" id="camilla-loading-overlay">
      <div class="loading-content">
        <div class="spinner-wrapper">
          <svg class="spinner" width="50" height="50" viewBox="0 0 50 50">
            <circle class="spinner-path" cx="25" cy="25" r="20" fill="none" stroke-width="4"></circle>
          </svg>
        </div>
        <h3 class="loading-title">🔄 Searching with Camilla</h3>
        <p class="loading-subtitle">This may take up to 15 seconds</p>
        <div class="loading-dots">
          <span></span>
          <span></span>
          <span></span>
        </div>
      </div>
    </div>
  `;

  /**
   * Show loading message
   */
  window.showCamillaLoading = function() {
    // Remove existing overlay if present
    hideCamillaLoading();

    // Create and insert overlay
    const overlay = document.createElement('div');
    overlay.innerHTML = loadingHTML;
    document.body.appendChild(overlay.firstElementChild);

    // Prevent body scroll
    document.body.style.overflow = 'hidden';

    // Track analytics
    if (window.trackEvent) {
      window.trackEvent('loading_message_shown', {
        timestamp: new Date().toISOString()
      });
    }

    // Auto-hide after 20 seconds (fallback)
    window.camillaLoadingTimeout = setTimeout(() => {
      hideCamillaLoading();
      console.warn('Camilla search timed out after 20 seconds');

      // Show error in modal if it exists
      const errorText = document.getElementById('ai-error-text');
      const errorMessage = document.getElementById('ai-error-message');
      if (errorText && errorMessage) {
        errorText.textContent = 'Search is taking longer than expected. Please try again.';
        errorMessage.style.display = 'block';
      }
    }, 20000);
  };

  /**
   * Hide loading message
   */
  window.hideCamillaLoading = function() {
    const overlay = document.getElementById('camilla-loading-overlay');
    if (overlay) {
      overlay.classList.add('fade-out');
      setTimeout(() => {
        overlay.remove();
        document.body.style.overflow = '';
      }, 300);
    }

    // Clear timeout
    if (window.camillaLoadingTimeout) {
      clearTimeout(window.camillaLoadingTimeout);
      window.camillaLoadingTimeout = null;
    }

    // Track analytics
    if (window.trackEvent) {
      window.trackEvent('loading_message_hidden', {
        timestamp: new Date().toISOString()
      });
    }
  };

  /**
   * Hide loading when AI modal receives response
   */
  document.addEventListener('DOMContentLoaded', function() {
    // Monitor AI chat messages for new content
    const chatMessages = document.getElementById('ai-chat-messages');
    if (chatMessages) {
      const observer = new MutationObserver(function(mutations) {
        mutations.forEach(function(mutation) {
          if (mutation.addedNodes.length > 0) {
            // New message added - hide loading
            hideCamillaLoading();
          }
        });
      });

      observer.observe(chatMessages, {
        childList: true,
        subtree: true
      });
    }

    // Also hide on error message display
    const errorMessage = document.getElementById('ai-error-message');
    if (errorMessage) {
      const errorObserver = new MutationObserver(function(mutations) {
        mutations.forEach(function(mutation) {
          if (mutation.target.style.display === 'block') {
            hideCamillaLoading();
          }
        });
      });

      errorObserver.observe(errorMessage, {
        attributes: true,
        attributeFilter: ['style']
      });
    }
  });

  /**
   * Analytics tracking helper
   */
  window.trackEvent = window.trackEvent || function(eventName, data) {
    console.log('Track Event:', eventName, data);

    // Google Analytics (if available)
    if (typeof gtag !== 'undefined') {
      gtag('event', eventName, data);
    }

    // Shopify Analytics (if available)
    if (typeof ShopifyAnalytics !== 'undefined') {
      ShopifyAnalytics.lib.track(eventName, data);
    }
  };

})();
