# Ask Camilla - Dual-Mode Search Implementation Plan

## Overview
Implement a dual-mode search system allowing users to toggle between traditional Shopify search and AI-powered conversational search ("Ask Camilla").

---

## Features

### 1. Dual-Mode Search Bar (Header)
Replace existing header search with enhanced version featuring:
- **Standard Search Mode** 🔍 - Traditional Shopify product search with instant dropdown results
- **Ask Camilla Mode** ✨ - AI-powered conversational search opening modal interface

### 2. Floating AI Button (Bottom-Right)
Always-accessible floating button providing quick access to Ask Camilla from anywhere on the site.

### 3. Loading Indicator
When user switches to "Ask Camilla" mode and submits search:
- Display message: **"Searching with Camilla... This may take up to 15 seconds"**
- Show loading animation
- Prevents user confusion during AI response time

---

## User Experience Flow

### Standard Search Mode
```
1. User sees search bar with toggle: [Search 🔍] [Ask Camilla ✨]
2. "Search 🔍" is active by default
3. User types query → Instant Shopify dropdown appears
4. User clicks result → Goes to product page
```

### Ask Camilla Mode
```
1. User clicks "Ask Camilla ✨" toggle
2. Placeholder changes: "Ask Camilla to find products..."
3. User types conversational query: "I need hobby paints for plastic models"
4. User presses Enter or clicks search icon
5. Loading message appears: "Searching with Camilla... This may take up to 15 seconds"
6. AI modal opens with Camilla's response
7. User can continue conversation in modal
```

### Floating Button
```
1. User scrolls anywhere on site
2. Floating button visible bottom-right
3. Tooltip on hover: "Ask Camilla (⌘K)"
4. Click → Opens AI modal immediately
5. Keyboard shortcut Cmd+K → Opens modal
```

---

## Visual Design

### Toggle Design
```
┌────────────────────────────────────────────────────────┐
│  ┌─────────────┐ ┌──────────────┐                     │
│  │  Search 🔍  │ │ Ask Camilla ✨│  [Search box...  🔍]│
│  │  (active)   │ │  (inactive)  │                     │
│  └─────────────┘ └──────────────┘                     │
└────────────────────────────────────────────────────────┘
```

**Active State:**
- Bold text
- Primary color background (#212b36)
- White text

**Inactive State:**
- Regular text
- Light gray background
- Dark gray text
- Hover: Slight color change

### Floating Button Design
```
     ┌─────────────────┐
     │  Ask Camilla    │ ← Tooltip
     │     (⌘K)        │
     └────────┬────────┘
              │
         ┌────▼────┐
         │    ✨   │  ← Circular button
         │         │     Chat bubble icon
         └─────────┘     with sparkle
```

**Position:** Fixed bottom-right (20px from bottom, 20px from right)
**Size:** 60px diameter
**Animation:** Subtle pulse/glow every 3 seconds
**Z-index:** 1000 (below modal but above content)

---

## Loading State Message

### When User Submits in Ask Camilla Mode:

**Overlay Message:**
```
┌──────────────────────────────────────┐
│                                      │
│        🔄 Searching with Camilla     │
│                                      │
│   This may take up to 15 seconds    │
│                                      │
│    [Animated spinner/dots]           │
│                                      │
└──────────────────────────────────────┘
```

**Display Logic:**
1. User submits query in "Ask Camilla" mode
2. Show loading overlay immediately
3. Open modal in background
4. Send API request
5. When response received → Hide loading overlay, show modal with response
6. If > 15 seconds → Keep showing message
7. On error → Hide loading, show error in modal

**Styling:**
- Semi-transparent dark overlay (rgba(0, 0, 0, 0.7))
- White text, center-aligned
- Loading spinner with primary brand color
- Smooth fade-in/fade-out animation

---

## Technical Implementation

### Files to Create

#### 1. `/shopify-theme-extension/blocks/dual-search-bar.liquid`
Dual-mode search bar component with toggle functionality.

**Features:**
- Toggle switch between Standard/Ask Camilla modes
- State management (active mode)
- Placeholder text changes based on mode
- Event handlers for mode switching
- Integration with existing Shopify search
- Form submission handling

**Settings:**
```json
{
  "name": "Dual Search Bar",
  "settings": [
    {
      "type": "color",
      "id": "primary_color",
      "label": "Primary Color",
      "default": "#212b36"
    },
    {
      "type": "text",
      "id": "standard_placeholder",
      "label": "Standard Search Placeholder",
      "default": "Search products..."
    },
    {
      "type": "text",
      "id": "ai_placeholder",
      "label": "Ask Camilla Placeholder",
      "default": "Ask Camilla to find products..."
    }
  ]
}
```

#### 2. `/shopify-theme-extension/blocks/floating-ai-button.liquid`
Floating button component for quick AI access.

**Features:**
- Fixed position bottom-right
- Tooltip with keyboard shortcut
- Click handler to open modal
- Smooth show/hide on scroll (optional)
- Pulse animation

**Settings:**
```json
{
  "name": "Floating AI Button",
  "settings": [
    {
      "type": "color",
      "id": "button_color",
      "label": "Button Color",
      "default": "#212b36"
    },
    {
      "type": "checkbox",
      "id": "show_on_mobile",
      "label": "Show on Mobile",
      "default": true
    },
    {
      "type": "checkbox",
      "id": "hide_on_scroll_up",
      "label": "Hide when scrolling up",
      "default": false
    }
  ]
}
```

#### 3. `/shopify-theme-extension/assets/dual-search.js`
JavaScript for dual-mode search functionality.

**Functions:**
- `initDualSearch()` - Initialize toggle and mode switching
- `switchMode(mode)` - Handle mode switching (standard/ai)
- `handleSubmit(event)` - Form submission based on mode
- `showLoadingMessage()` - Display "searching..." overlay
- `hideLoadingMessage()` - Hide loading overlay
- `openAIModal(query)` - Open modal with pre-filled query

#### 4. `/shopify-theme-extension/assets/dual-search.css`
Styles for dual-mode search components.

**Sections:**
- Toggle switch styles
- Active/inactive states
- Search bar styling
- Loading overlay
- Floating button
- Responsive mobile styles

---

## JavaScript Behavior

### Mode Switching
```javascript
// Pseudocode
function switchMode(mode) {
  if (mode === 'standard') {
    // Activate standard search
    searchInput.placeholder = "Search products..."
    searchForm.action = "/search"
    searchForm.method = "GET"
    enableShopifyDropdown()
  } else if (mode === 'ai') {
    // Activate Ask Camilla mode
    searchInput.placeholder = "Ask Camilla to find products..."
    disableShopifyDropdown()
  }

  updateToggleUI(mode)
}
```

### Form Submission
```javascript
// Pseudocode
function handleSubmit(event) {
  const currentMode = getCurrentMode()

  if (currentMode === 'standard') {
    // Allow normal form submission to Shopify search
    return true
  } else if (currentMode === 'ai') {
    // Prevent normal submission
    event.preventDefault()

    const query = searchInput.value

    // Show loading message
    showLoadingMessage()

    // Open modal with query
    openAIModal(query)

    // Loading message will hide when modal loads response
  }
}
```

### Loading Message
```javascript
// Pseudocode
function showLoadingMessage() {
  const overlay = document.createElement('div')
  overlay.className = 'camilla-loading-overlay'
  overlay.innerHTML = `
    <div class="loading-content">
      <div class="spinner"></div>
      <h3>🔄 Searching with Camilla</h3>
      <p>This may take up to 15 seconds</p>
    </div>
  `
  document.body.appendChild(overlay)

  // Auto-hide after 20 seconds (fallback)
  setTimeout(() => hideLoadingMessage(), 20000)
}

function hideLoadingMessage() {
  const overlay = document.querySelector('.camilla-loading-overlay')
  if (overlay) {
    overlay.classList.add('fade-out')
    setTimeout(() => overlay.remove(), 300)
  }
}
```

---

## Integration Steps

### Step 1: Create Component Files
1. Create `dual-search-bar.liquid`
2. Create `floating-ai-button.liquid`
3. Create `dual-search.js`
4. Create `dual-search.css`

### Step 2: Update Theme Files
1. Add CSS to `ai-search-assets.liquid`:
   ```liquid
   <link rel="stylesheet" href="{{ 'dual-search.css' | asset_url }}">
   ```

2. Add JS to `ai-search-assets.liquid`:
   ```liquid
   <script src="{{ 'dual-search.js' | asset_url }}" defer></script>
   ```

3. Update header section to include dual-search-bar block

4. Add floating button to footer or theme.liquid:
   ```liquid
   {% section 'floating-ai-button' %}
   ```

### Step 3: Upload to Shopify
1. Upload new files to Shopify Admin → Edit Code
2. Update theme sections/blocks
3. Test on staging theme first

### Step 4: Testing Checklist
- [ ] Toggle switches between modes correctly
- [ ] Standard search shows Shopify dropdown
- [ ] Ask Camilla mode opens modal
- [ ] Loading message appears for AI search
- [ ] Loading message disappears when modal loads
- [ ] Floating button opens modal
- [ ] Keyboard shortcut (Cmd+K) works
- [ ] Mobile responsive design works
- [ ] Both searches work on all pages

---

## Placeholder Text Examples

### Standard Search Mode:
- "Search products..."
- "Find items..."
- "What are you looking for?"

### Ask Camilla Mode:
- "Ask Camilla to find products..." ✅ (Recommended)
- "Ask me anything about our products..."
- "Describe what you're looking for..."

---

## Loading Message Variations

**Option 1 (Recommended):**
```
🔄 Searching with Camilla
This may take up to 15 seconds
```

**Option 2:**
```
✨ Camilla is searching for you
Please wait up to 15 seconds...
```

**Option 3:**
```
💬 Camilla is thinking...
Responses typically take 10-15 seconds
```

---

## Keyboard Shortcuts

### Global Shortcuts:
- **Cmd+K (Mac) / Ctrl+K (Windows):** Open Ask Camilla modal
- **Escape:** Close modal
- **Tab:** Switch between toggle buttons when focused

### In-Modal Shortcuts:
- **Enter:** Send message
- **Escape:** Close modal

---

## Mobile Considerations

### Responsive Breakpoints:
- **Desktop (> 768px):**
  - Full toggle visible
  - Floating button bottom-right (60px diameter)

- **Tablet (768px - 480px):**
  - Compact toggle (smaller text)
  - Floating button bottom-right (50px diameter)

- **Mobile (< 480px):**
  - Toggle as dropdown or icon switch
  - Floating button bottom-center (55px diameter)
  - Loading message full-screen

### Touch Interactions:
- Larger touch targets (minimum 44px)
- Swipe to switch modes (optional)
- Tap floating button to open

---

## Accessibility

### ARIA Labels:
```html
<div role="group" aria-label="Search mode selector">
  <button
    role="tab"
    aria-selected="true"
    aria-controls="search-input"
    id="standard-search-tab">
    Search 🔍
  </button>
  <button
    role="tab"
    aria-selected="false"
    aria-controls="search-input"
    id="ai-search-tab">
    Ask Camilla ✨
  </button>
</div>

<input
  type="text"
  role="searchbox"
  aria-labelledby="standard-search-tab"
  aria-describedby="search-mode-hint"
/>
```

### Screen Reader Announcements:
- "Switched to standard search mode"
- "Switched to Ask Camilla mode - conversational AI search"
- "Searching with Camilla, please wait up to 15 seconds"
- "Results loaded from Camilla"

### Keyboard Navigation:
- Tab through toggle buttons
- Arrow keys to switch modes
- Enter to submit search
- Focus trap in modal when open

---

## Performance Considerations

### Loading Optimization:
1. Lazy load floating button (after initial page load)
2. Preload AI modal HTML (hidden)
3. Cache toggle state in sessionStorage
4. Debounce mode switching (prevent rapid clicks)

### API Optimization:
1. Show loading immediately (don't wait for network)
2. Timeout handling (20 seconds max)
3. Error handling with user-friendly messages
4. Retry mechanism (optional)

---

## Analytics Tracking

### Events to Track:
1. **Mode Switch:**
   - `camilla_mode_selected`
   - `standard_mode_selected`

2. **Search Submissions:**
   - `standard_search_submitted` (query)
   - `camilla_search_submitted` (query)

3. **Floating Button:**
   - `floating_button_clicked`
   - `keyboard_shortcut_used`

4. **Loading Experience:**
   - `loading_message_shown`
   - `loading_duration` (time until response)

5. **Conversions:**
   - `camilla_product_clicked`
   - `camilla_cart_added`

---

## Error Handling

### API Errors:
```
❌ Oops! Camilla couldn't connect.
Please try again or use standard search.
[Try Again] [Use Standard Search]
```

### Timeout (> 20 seconds):
```
⏱️ This is taking longer than expected.
Camilla is still searching...
[Keep Waiting] [Cancel]
```

### No Results:
```
🤔 Camilla couldn't find matches for that.
Try rephrasing or use standard search.
[Try Again] [Standard Search]
```

---

## Future Enhancements

### Phase 2 (Post-Launch):
1. **Smart Suggestions:** Show "Ask Camilla" prompts for complex queries
2. **Mode Preferences:** Remember user's preferred mode
3. **Voice Search:** Microphone input for Ask Camilla
4. **Quick Actions:** "Find similar", "Compare products" buttons
5. **Analytics Dashboard:** Track mode usage, conversion rates

### Phase 3:
1. **Inline AI Results:** Show AI suggestions in standard search dropdown
2. **Hybrid Mode:** Combine both search types
3. **Personalization:** Learn user preferences over time

---

## Success Metrics

### KPIs to Monitor:
1. **Adoption Rate:** % of searches using Ask Camilla
2. **Engagement:** Average conversation length
3. **Conversion:** Sales from Ask Camilla vs standard
4. **User Satisfaction:** Feedback/ratings
5. **Performance:** Average response time

### Target Goals (3 months):
- 15-20% of searches use Ask Camilla
- 30% higher conversion rate than standard search
- < 12 second average response time
- 4+ star average rating

---

## Rollout Plan

### Week 1: Development
- Create all component files
- Implement toggle functionality
- Build loading message system
- Create floating button

### Week 2: Testing
- Test on staging environment
- User acceptance testing (5-10 users)
- Fix bugs and refine UX
- Mobile device testing

### Week 3: Soft Launch
- Deploy to 10% of traffic (A/B test)
- Monitor analytics and errors
- Gather user feedback
- Optimize based on data

### Week 4: Full Launch
- Roll out to 100% of users
- Announce via email/social media
- Monitor performance closely
- Iterate based on feedback

---

## Support & Maintenance

### Documentation for Support Team:
- How to guide users between search modes
- Common Ask Camilla queries and responses
- Troubleshooting guide for loading issues
- Escalation path for AI errors

### Monitoring:
- Error rate alerts (> 5% of requests)
- Performance alerts (> 20s response time)
- Usage analytics (daily/weekly reports)
- User feedback collection

---

## Appendix

### Related Files:
- `/shopify-theme-extension/blocks/search-modal.liquid` - AI modal
- `/shopify-theme-extension/assets/ai-search-client.js` - Modal logic
- `/shopify-theme-extension/snippets/ai-search-assets.liquid` - Asset loader
- `theme.liquid` - Main theme file

### API Endpoints:
- `POST /api/shopify/chat/message` - Send message to Camilla
- Query params: `?shop=hearnshobbies.myshopify.com`

### Design Assets Needed:
- Camilla logo/icon (for floating button)
- Loading spinner animation
- Toggle switch icons
- Error state illustrations

---

**Document Version:** 1.0
**Last Updated:** 2025-11-13
**Status:** Ready for Implementation
