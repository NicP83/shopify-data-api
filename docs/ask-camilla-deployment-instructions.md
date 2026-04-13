# Ask Camilla - Deployment Instructions

## ✅ Implementation Complete!

All components for the "Ask Camilla" dual-mode search have been created and are ready for deployment.

---

## Files Created

### 1. Components (Liquid)
- ✅ `/shopify-theme-extension/blocks/dual-search-bar.liquid` (5.4 KB)
- ✅ `/shopify-theme-extension/blocks/floating-ai-button.liquid` (4.2 KB)

### 2. Assets (JavaScript)
- ✅ `/shopify-theme-extension/assets/dual-search.js` (3.1 KB)

### 3. Assets (CSS)
- ✅ `/shopify-theme-extension/assets/dual-search.css` (4.7 KB)

### 4. Updated Files
- ✅ `/shopify-theme-extension/snippets/ai-search-assets.liquid` (added new asset links)

---

## Upload to Shopify - Step by Step

### Step 1: Upload Asset Files

**Go to:** Shopify Admin → Online Store → Themes → Actions → **Edit Code**

**Upload to Assets folder:**
1. Click "**Assets**" in left sidebar
2. Click "**Add a new asset**"
3. Upload these files:
   - `dual-search.js`
   - `dual-search.css`
4. Click "**Save**"

### Step 2: Upload Component Blocks

**Go to:** Shopify Admin → Online Store → Themes → Actions → **Edit Code**

**Upload to Blocks folder:**
1. Click "**Blocks**" in left sidebar
2. Click "**Add a new snippet**"
3. Name it `dual-search-bar` and paste contents from `dual-search-bar.liquid`
4. Click "**Save**"
5. Repeat for `floating-ai-button` (name it `floating-ai-button`)

### Step 3: Update Assets Loader

**Go to:** Shopify Admin → Online Store → Themes → Actions → **Edit Code**

1. Click "**Snippets**" → `ai-search-assets.liquid`
2. Replace the content with updated version
3. Click "**Save**"

### Step 4: Add Components to Theme

**Option A: Via Theme Editor (Recommended)**
1. Go to **Online Store → Themes → Customize**
2. Click on header section
3. Click "**Add block**" → Find "**Dual Search Bar**"
4. Customize settings (primary color, placeholders)
5. Click "**Save**"

**Option B: Via Code (theme.liquid)**
1. Go to **Edit Code** → `theme.liquid`
2. Find your current search in the header
3. Replace with:
   ```liquid
   {% section 'dual-search-bar' %}
   ```
4. Add floating button before `</body>`:
   ```liquid
   {% section 'floating-ai-button' %}
   ```
5. Click "**Save**"

---

## Testing Checklist

Before going live, test these features:

### Dual-Mode Search Bar
- [ ] Toggle switches between "Search" and "Ask Camilla"
- [ ] Placeholder text changes based on mode
- [ ] Standard mode shows Shopify search results
- [ ] Ask Camilla mode shows loading message
- [ ] Loading message: "Searching with Camilla... may take up to 15 seconds"
- [ ] AI modal opens with user's query pre-filled
- [ ] Toggle state persists (sessionStorage)

### Floating Button
- [ ] Button visible on all pages
- [ ] Button opens AI modal
- [ ] Tooltip shows on hover
- [ ] Keyboard shortcut (Cmd+K / Ctrl+K) works
- [ ] Pulse animation on first visit
- [ ] Button hides on mobile (if setting enabled)

### Loading Experience
- [ ] Loading overlay appears immediately when submitting AI search
- [ ] Message displays: "🔄 Searching with Camilla"
- [ ] Subtitle: "This may take up to 15 seconds"
- [ ] Animated spinner and dots
- [ ] Loading disappears when response received
- [ ] Loading auto-hides after 20 seconds (timeout)

### Mobile Responsive
- [ ] Toggle layout works on mobile
- [ ] Floating button positioned correctly
- [ ] Loading message fits mobile screens
- [ ] Touch interactions work smoothly

### Accessibility
- [ ] Keyboard navigation through toggle
- [ ] ARIA labels present
- [ ] Screen reader announcements work
- [ ] Focus management in modal
- [ ] Reduced motion preferences respected

---

## Configuration Options

### Dual Search Bar Settings (Customizer)
```
Primary Color: #212b36 (default)
Standard Placeholder: "Search products..."
Ask Camilla Placeholder: "Ask Camilla to find products..."
```

### Floating Button Settings (Customizer)
```
Button Color: #212b36 (default)
Show on Mobile: true (default)
Hide on scroll up: false (default)
```

---

## User Journey

### Standard Search Flow
```
1. User sees toggle with "Search" active
2. Types product name
3. Sees instant Shopify dropdown results
4. Clicks result → Product page
```

### Ask Camilla Flow
```
1. User clicks "Ask Camilla" toggle
2. Placeholder changes to "Ask Camilla to find products..."
3. User types conversational query
4. Presses Enter or clicks search icon
5. Loading message appears: "Searching with Camilla... may take up to 15 seconds"
6. AI modal opens with response
7. User continues conversation
```

### Floating Button Flow
```
1. User scrolls page
2. Sees floating button bottom-right
3. Hovers → Tooltip: "Ask Camilla (⌘K)"
4. Clicks → AI modal opens
5. Starts conversation
```

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Cmd+K` (Mac) / `Ctrl+K` (Windows) | Open Ask Camilla modal |
| `Cmd+Shift+K` | Toggle between Search/Ask Camilla modes |
| `Escape` | Close AI modal |
| `Tab` | Navigate between toggle buttons |

---

## Analytics Events

These events are automatically tracked (if analytics configured):

### Search Events
- `camilla_mode_selected` - User switches to Ask Camilla
- `standard_mode_selected` - User switches to Standard search
- `camilla_search_submitted` - Query submitted to Camilla
- `standard_search_submitted` - Query submitted to Shopify search

### Button Events
- `floating_button_clicked` - Floating button clicked
- `keyboard_shortcut_used` - Cmd+K used

### Loading Events
- `loading_message_shown` - Loading overlay displayed
- `loading_message_hidden` - Loading overlay dismissed
- `loading_duration` - Time from submit to response

---

## Troubleshooting

### Issue: Toggle not switching modes
**Solution:** Check browser console for JavaScript errors. Ensure `dual-search.js` is loaded.

### Issue: Loading message not appearing
**Solution:** Verify `showCamillaLoading()` function exists. Check `dual-search.js` is loaded before form submission.

### Issue: AI modal not opening
**Solution:** Ensure `search-modal.liquid` is in Sections and loaded via `theme.liquid`. Check modal ID is `ai-search-modal`.

### Issue: Floating button not visible
**Solution:** Check z-index conflicts. Verify `floating-ai-button.liquid` is added to theme.

### Issue: Loading timeout (20 seconds)
**Solution:** Check backend API response time. Verify network connection. Consider increasing timeout in `dual-search.js` line 76.

### Issue: Standard search not working
**Solution:** Ensure form action="/search" and method="get" in standard mode. Check Shopify search is enabled.

---

## Performance Notes

### Load Times
- **dual-search.js:** ~3 KB (loads immediately)
- **dual-search.css:** ~4.7 KB (loads with page)
- **Total additional load:** ~7.7 KB

### API Response Times
- **Ask Camilla:** Typically 10-15 seconds (backend AI processing)
- **Standard Search:** Instant (Shopify native)

### Caching
- Toggle state cached in sessionStorage (persists across page refreshes)
- First-visit hint cached in localStorage (shows once)

---

## Next Steps

### Phase 1: Soft Launch (Week 1)
1. Deploy to 10% of traffic (A/B test)
2. Monitor analytics dashboard
3. Track error rates and response times
4. Gather user feedback

### Phase 2: Optimization (Week 2)
1. Adjust timeout based on actual response times
2. Refine loading message copy based on feedback
3. Optimize toggle UI based on usage patterns
4. Fix any bugs discovered

### Phase 3: Full Launch (Week 3)
1. Roll out to 100% of users
2. Announce feature via email/social media
3. Create help documentation for customers
4. Monitor success metrics

---

## Success Metrics (Track These)

### Adoption
- % of searches using Ask Camilla vs Standard
- Floating button click-through rate
- Keyboard shortcut usage rate

### Engagement
- Average conversation length in Ask Camilla
- Repeat usage rate (users who use it again)
- Time spent in modal

### Business Impact
- Conversion rate: Ask Camilla vs Standard search
- Average order value from each search type
- Product discovery rate (products found via Camilla)

### Technical Performance
- Average loading time
- Error rate (%)
- Timeout rate (%)

---

## Support Documentation

### For Customers
Create a help article explaining:
- "What is Ask Camilla?"
- "How to use conversational search"
- "Example queries that work well"
- "Keyboard shortcuts"

### For Support Team
- How to guide users between search modes
- Common issues and solutions
- When to escalate to technical team

---

## Rollback Plan

If issues arise, you can quickly rollback:

1. **Remove Dual Search Bar:**
   - Go to Theme Customizer → Header
   - Remove "Dual Search Bar" block
   - Add back original search

2. **Remove Floating Button:**
   - Go to Theme Customizer
   - Remove "Floating AI Button" section

3. **Revert Assets:**
   - Keep files but remove links from `ai-search-assets.liquid`

The original AI modal will continue working via the test page and keyboard shortcut.

---

## Future Enhancements

### Phase 4 (Post-Launch)
- [ ] Voice search integration
- [ ] Smart query suggestions
- [ ] Product comparison feature
- [ ] Personalized recommendations
- [ ] Multi-language support

### Phase 5 (Advanced)
- [ ] Hybrid search (combine Standard + AI)
- [ ] Visual search (image upload)
- [ ] AR product preview integration
- [ ] Chat history persistence

---

## Contact & Support

**Implementation Questions:**
- Check `/docs/ask-camilla-implementation-plan.md`
- Review this deployment guide

**Technical Issues:**
- Check browser console for errors
- Verify all files uploaded correctly
- Test in incognito mode (no cache)

**Feature Requests:**
- Track in project backlog
- Prioritize based on user feedback

---

## ✅ Ready to Deploy!

All files are created and ready. Follow the steps above to upload to Shopify and go live!

**Estimated Time to Deploy:** 30-45 minutes

**Good luck with the launch! 🚀**
