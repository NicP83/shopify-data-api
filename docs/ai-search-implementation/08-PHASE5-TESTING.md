# Phase 5: Integration Testing
## End-to-End Testing and Quality Assurance

**Duration:** 0.5-1 day (4-7 hours)

---

## Overview

Phase 5 conducts comprehensive testing of the entire AI-enhanced search system:

1. OAuth flow testing
2. Shop-scoped chat functionality
3. Theme extension integration
4. Admin product search
5. Mobile and cross-browser testing
6. Performance benchmarking
7. Security audit

---

## Test Plan

### 1. OAuth Installation Flow

**Test Case:** Install app on development store

**Steps:**
1. Open install URL: `https://your-app.railway.app/shopify/install?shop=test-shop.myshopify.com`
2. Verify redirect to Shopify OAuth page
3. Click "Install app"
4. Verify redirect to callback URL
5. Verify redirect to admin dashboard

**Expected Results:**
- ✅ OAuth page loads correctly
- ✅ HMAC signature verified
- ✅ Access token exchanged
- ✅ Shop saved to database
- ✅ No errors in browser console
- ✅ No errors in server logs

**Database Verification:**
```sql
SELECT * FROM shopify_shops WHERE shop_domain = 'test-shop.myshopify.com';
```

---

### 2. Shop-Scoped Chat API

**Test Case:** Send message to chat API

**Request:**
```bash
curl -X POST "https://your-app.railway.app/api/shopify/chat/message?shop=hearnshobbies.myshopify.com" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Show me Gundam model kits for beginners",
    "conversationHistory": []
  }'
```

**Expected Response:**
```json
{
  "response": "I'd be happy to help you find beginner-friendly Gundam kits! Here are my top recommendations:...",
  "products": [
    {
      "id": "gid://shopify/Product/123",
      "title": "RG RX-78-2 Gundam",
      "price": "28.99",
      "currency": "USD",
      "image": "https://...",
      "url": "https://hearnshobbies.com/products/...",
      "inStock": true,
      "vendor": "Bandai",
      "tags": ["gundam", "real-grade"]
    }
  ],
  "conversationId": "conv_abc123",
  "timestamp": "2025-10-30T15:30:00Z"
}
```

**Verification Checklist:**
- ✅ Response received within 5 seconds
- ✅ `products` array contains 2-4 products
- ✅ Product data complete (image, price, URL)
- ✅ Response uses shop currency (USD)
- ✅ Conversation ID generated

**Error Scenarios:**
```bash
# Invalid shop
curl "https://your-app.railway.app/api/shopify/chat/message?shop=invalid-shop.myshopify.com" \
  -X POST -H "Content-Type: application/json" -d '{"message": "test"}'

# Expected: 404 Not Found

# AI disabled shop (update shop first: ai_enabled = false)
curl "https://your-app.railway.app/api/shopify/chat/message?shop=disabled-shop.myshopify.com" \
  -X POST -H "Content-Type: application/json" -d '{"message": "test"}'

# Expected: 403 Forbidden
```

---

### 3. Theme Extension Integration

**Test Case:** AI button appears on storefront

**Steps:**
1. Install app on development store
2. Go to Shopify Admin → Online Store → Themes
3. Click "Customize" on active theme
4. Open "App embeds" section
5. Enable "AI Search Assistant"
6. Save
7. Visit storefront: `https://test-shop.myshopify.com`

**Expected Results:**
- ✅ AI button (🤖) appears next to search bar
- ✅ Button styled correctly
- ✅ Button responsive on mobile

**Test Case:** AI modal opens and functions

**Steps:**
1. Click AI button (🤖)
2. Verify modal opens
3. Type "gundam kits"
4. Submit message
5. Verify loading indicator
6. Verify response appears
7. Verify product cards render
8. Click product card
9. Verify redirects to product page

**Expected Results:**
- ✅ Modal opens smoothly (< 300ms)
- ✅ Input field focused
- ✅ Loading indicator visible during API call
- ✅ Response appears within 5 seconds
- ✅ Product cards render with images
- ✅ Product cards clickable
- ✅ Modal closeable (overlay, X button)

---

### 4. Admin Product Search

**Test Case:** Quick search mode

**Steps:**
1. Navigate to `/product-search`
2. Verify "Quick Search" mode selected
3. Enter "gundam" in search
4. Click "🔍 Search"
5. Verify results appear

**Expected Results:**
- ✅ Results load quickly (< 1 second)
- ✅ Product cards render correctly
- ✅ Product data complete (image, title, price)
- ✅ Results count displayed

**Test Case:** AI assistant mode

**Steps:**
1. Toggle to "🤖 AI Assistant"
2. Type "What's a good beginner Gundam kit?"
3. Submit message
4. Verify AI response
5. Verify product cards in chat

**Expected Results:**
- ✅ Mode switches smoothly
- ✅ Chat interface appears
- ✅ AI responds conversationally
- ✅ Product cards embedded in chat
- ✅ Products clickable

**Test Case:** Mode switching

**Steps:**
1. Search in Quick mode
2. Switch to AI mode
3. Send AI message
4. Switch back to Quick mode
5. Verify previous search results preserved

**Expected Results:**
- ✅ Quick search results persist
- ✅ AI chat history preserved
- ✅ No errors on mode switch

---

### 5. Mobile Responsiveness

**Devices to Test:**
- iPhone 13 Pro (390x844)
- iPhone SE (375x667)
- Samsung Galaxy S21 (360x800)
- iPad Pro (1024x1366)

**Test Cases:**

**Storefront AI Button:**
- ✅ Button visible on mobile
- ✅ Text hidden, icon only on small screens
- ✅ Button not overlapping search input

**AI Modal:**
- ✅ Modal fills screen on mobile
- ✅ Chat messages readable
- ✅ Product cards stack vertically
- ✅ Input field accessible (no keyboard overlap)
- ✅ Scrolling smooth

**Admin Product Search:**
- ✅ Toggle buttons accessible
- ✅ Search input full width
- ✅ Product cards 1 column on mobile
- ✅ Chat interface readable

---

### 6. Cross-Browser Testing

**Browsers:**
- Chrome (latest)
- Safari (latest)
- Firefox (latest)
- Edge (latest)

**Test Matrix:**

| Feature | Chrome | Safari | Firefox | Edge |
|---------|--------|--------|---------|------|
| OAuth flow | ✅ | ✅ | ✅ | ✅ |
| AI button renders | ✅ | ✅ | ✅ | ✅ |
| Modal opens | ✅ | ✅ | ✅ | ✅ |
| Chat sends message | ✅ | ✅ | ✅ | ✅ |
| Product cards render | ✅ | ✅ | ✅ | ✅ |
| Admin search works | ✅ | ✅ | ✅ | ✅ |

---

### 7. Performance Testing

**Metrics to Measure:**

**API Response Times (p95):**
- OAuth callback: < 1 second
- Chat message: < 5 seconds
- Product search: < 1 second
- Shop config API: < 500ms

**Frontend Performance:**
- Modal open: < 300ms
- Search input → results: < 100ms (quick mode)
- Chat message render: < 100ms

**Load Testing (Optional):**
```bash
# Install Apache Bench
brew install ab  # macOS

# Test chat API (10 concurrent users, 100 requests)
ab -n 100 -c 10 -p message.json -T application/json \
  "https://your-app.railway.app/api/shopify/chat/message?shop=hearnshobbies.myshopify.com"
```

**Expected Results:**
- ✅ 95% of requests complete within 5 seconds
- ✅ 0% error rate
- ✅ Backend handles concurrent requests

---

### 8. Security Testing

**Test Cases:**

**CORS Configuration:**
```bash
# Test CORS from allowed origin
curl -H "Origin: https://hearnshobbies.com" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Content-Type" \
  -X OPTIONS \
  "https://your-app.railway.app/api/shopify/chat/message"

# Expected: 200 OK with CORS headers

# Test CORS from disallowed origin
curl -H "Origin: https://evil-site.com" \
  -H "Access-Control-Request-Method: POST" \
  -X OPTIONS \
  "https://your-app.railway.app/api/shopify/chat/message"

# Expected: No CORS headers (request blocked)
```

**HMAC Verification:**
```bash
# Test OAuth callback with invalid HMAC
curl "https://your-app.railway.app/shopify/callback?shop=test.myshopify.com&code=abc&state=xyz&hmac=invalid"

# Expected: 401 Unauthorized or 400 Bad Request
```

**Input Validation:**
```bash
# Test with missing shop parameter
curl -X POST "https://your-app.railway.app/api/shopify/chat/message" \
  -H "Content-Type: application/json" \
  -d '{"message": "test"}'

# Expected: 400 Bad Request

# Test with SQL injection attempt
curl -X POST "https://your-app.railway.app/api/shopify/chat/message?shop=test'; DROP TABLE shopify_shops;--" \
  -H "Content-Type: application/json" \
  -d '{"message": "test"}'

# Expected: 400 Bad Request (invalid shop format)
```

**Access Token Security:**
```bash
# Verify access token not in API responses
curl "https://your-app.railway.app/api/shopify/shops/hearnshobbies.myshopify.com"

# Expected: Response should NOT include access_token field
```

---

### 9. Error Handling

**Test Cases:**

**Shopify API Down:**
- Simulate by providing invalid access token
- Expected: Graceful error message, no crash

**Claude API Timeout:**
- Simulate with very large maxTokens (> 10000)
- Expected: Timeout after 30 seconds, error message

**Database Connection Loss:**
- Stop database temporarily
- Expected: 500 error, logged, service recovers

**Invalid Product Search:**
- Search for non-existent product
- Expected: Empty results, no crash

---

### 10. Conversation Flow Testing

**Test Case:** Multi-turn conversation

**Conversation:**
1. User: "I'm new to Gundam"
2. AI: [Responds with beginner info]
3. User: "Show me beginner kits"
4. AI: [Shows 3 products]
5. User: "Tell me more about the first one"
6. AI: [Details about product 1]

**Verification:**
- ✅ Context maintained across turns
- ✅ AI references previous messages
- ✅ Product recommendations relevant
- ✅ Conversation history stored correctly

---

## Testing Checklist

### Backend
- [ ] OAuth installation works
- [ ] HMAC signature verification passes
- [ ] Shop saved to database correctly
- [ ] Chat API returns within 5 seconds
- [ ] Shop-specific system prompt used
- [ ] Products returned in response
- [ ] Shop configuration API functional
- [ ] CORS configured correctly
- [ ] Error handling works
- [ ] Input validation prevents injection

### Frontend (Storefront)
- [ ] AI button appears next to search
- [ ] Button responsive on mobile
- [ ] Modal opens smoothly
- [ ] Chat interface functional
- [ ] Product cards render correctly
- [ ] Loading indicator shows
- [ ] Error messages display
- [ ] Modal closeable
- [ ] Works on all browsers

### Frontend (Admin)
- [ ] Product search page loads
- [ ] Mode toggle works
- [ ] Quick search functional
- [ ] AI assistant mode functional
- [ ] Product cards render in chat
- [ ] Mode switching preserves state
- [ ] Mobile responsive
- [ ] Works on all browsers

### Performance
- [ ] API response < 5 seconds (p95)
- [ ] Modal opens < 300ms
- [ ] Quick search < 1 second
- [ ] No memory leaks
- [ ] Handles concurrent requests

### Security
- [ ] CORS properly configured
- [ ] HMAC verification prevents tampering
- [ ] Access tokens not exposed
- [ ] Input validation prevents injection
- [ ] HTTPS enforced in production

---

## Bug Tracking

Use this template to track any issues found:

### Bug Report Template

```markdown
## Bug #001: [Short Description]

**Severity:** Critical / High / Medium / Low
**Component:** Backend / Frontend / Theme Extension
**Environment:** Development / Production

**Steps to Reproduce:**
1. Step 1
2. Step 2
3. Step 3

**Expected Behavior:**
[What should happen]

**Actual Behavior:**
[What actually happens]

**Screenshots/Logs:**
[Attach if applicable]

**Status:** Open / In Progress / Fixed / Won't Fix
**Assigned To:** [Name]
**Fixed In:** [Commit hash or version]
```

---

## Phase 5 Deliverables

- [ ] All test cases executed
- [ ] Bug report created for any issues
- [ ] Critical bugs fixed
- [ ] Performance benchmarks met
- [ ] Security audit passed
- [ ] Mobile and browser testing complete
- [ ] Test results documented

---

## Next Phase

**Phase 6: Production Deployment** - Deploy to production and go live.

---

*Last Updated: 2025-10-30*
*Next: 09-PHASE6-DEPLOYMENT.md*
