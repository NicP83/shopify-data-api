# Shopify Theme Chatbot Connection Guide

**Last Updated:** November 5, 2025
**Status:** Ready to Connect

---

## Overview

This guide shows you how to connect your Shopify theme's AI chatbot to the Railway backend API.

**What You Have:**
- ✅ Shopify theme extension installed (AI search modal)
- ✅ Railway backend API deployed and running
- ✅ Database with shop configuration table

**What Needs Connection:**
- ❌ Theme settings need correct API URL
- ❌ Shop registration in database needs verification
- ❌ Test connection from storefront to backend

---

## Step 1: Verify Railway Backend is Running

### Check Backend Health

```bash
curl https://shopify-data-api-production.up.railway.app/api/health
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "service": "Shopify Data API",
    "status": "UP",
    "timestamp": 1762347322441
  },
  "error": null
}
```

✅ **Status**: Backend is UP and running!

---

## Step 2: Configure Shop in Database

### The Problem
The chat endpoint returns: `{"shop":"hearnshobbies.myshopify.com","error":"Shop not found or inactive"}`

This means the shop needs to be registered in the `shopify_shops` table.

### Solution: Set Environment Variable on Railway

The migration `V009__insert_hearns_hobbies_shop.sql` will automatically register your shop, but it needs the Shopify access token from an environment variable.

**On Railway Dashboard:**

1. Go to your project: `shopify-data-api-production`
2. Click on **Variables** tab
3. Add/Update this variable:

```
SHOPIFY_ACCESS_TOKEN=shpat_xxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

Replace `shpat_xxxxx...` with your actual Shopify Admin API access token from:
- Shopify Admin → Apps → [Your Custom App] → API credentials → Admin API access token

4. Click **Deploy** to restart with new environment variable

### Migration Will Run Automatically

When Railway restarts, the migration will execute:

```sql
INSERT INTO shopify_shops (
    shop_domain,
    access_token,
    is_active,
    ai_enabled,
    ai_model,
    ai_temperature,
    ai_max_tokens
) VALUES (
    'hearnshobbies.myshopify.com',
    '${SHOPIFY_ACCESS_TOKEN}',  -- Uses env variable
    true,
    true,
    'claude-3-7-sonnet-20250219',
    0.7,
    1024
);
```

---

## Step 3: Update Shopify Theme Settings

### Navigate to Theme Customizer

1. Go to **Shopify Admin**
2. Click **Online Store** → **Themes**
3. Click **Customize** on your active theme
4. Find the **AI Search Modal** block (should be in your theme sections)

### Configure API URL

In the AI Search Modal block settings, update:

**Setting Name**: `Backend API URL` or `api_url`

**Current Value** (placeholder):
```
https://your-app.railway.app/api/shopify/chat/message
```

**Change To** (actual Railway URL):
```
https://shopify-data-api-production.up.railway.app/api/shopify/chat/message
```

### Other Settings (Optional)

**Maximum Results**: `10` (default is good)
**Primary Color**: Your brand color (e.g., `#4A90E2`)
**Text Color**: `#333333` (default)

### Save Theme

Click **Save** in the top right corner of the theme customizer.

---

## Step 4: Test the Connection

### Test from Browser

1. Open your Shopify storefront: `https://hearnshobbies.com`
2. Click the **AI Search** button or press `Cmd/Ctrl + K`
3. The chat modal should open
4. Type a test message: **"Show me Gundam model kits"**
5. Press **Enter** or click **Send**

### Expected Behavior

**What You Should See:**
1. Typing indicator appears (animated dots)
2. AI response appears after 2-5 seconds
3. Product cards display with images, titles, and prices
4. Clicking a product card opens the product page

**Sample Response:**
```
AI: "Here are some great Gundam model kits I found for you:"

[Product Card 1: RG 1/144 RX-78-2 Gundam - $29.99]
[Product Card 2: MG 1/100 Freedom Gundam - $49.99]
[Product Card 3: PG 1/60 Unicorn Gundam - $199.99]
```

### Check Browser Console

Open browser Developer Tools (F12) and check the **Console** tab:

**Successful Request:**
```javascript
POST https://shopify-data-api-production.up.railway.app/api/shopify/chat/message?shop=hearnshobbies.myshopify.com

Request:
{
  "message": "Show me Gundam model kits",
  "conversationHistory": [],
  "maxResults": 10
}

Response: 200 OK
{
  "response": "Here are some great Gundam model kits...",
  "products": [...],
  "role": "assistant",
  "timestamp": "2025-11-05T..."
}
```

---

## Step 5: Test from Command Line (Optional)

Before testing on the storefront, you can test the API directly:

```bash
curl -X POST "https://shopify-data-api-production.up.railway.app/api/shopify/chat/message?shop=hearnshobbies.myshopify.com" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Show me Gundam model kits",
    "conversationHistory": [],
    "maxResults": 5
  }'
```

**Expected Response (after shop is registered):**
```json
{
  "response": "Here are some great Gundam model kits I found...",
  "products": [
    {
      "id": "123456789",
      "title": "RG 1/144 RX-78-2 Gundam",
      "handle": "rg-rx-78-2-gundam",
      "price": 29.99,
      "image": "https://cdn.shopify.com/..."
    }
  ],
  "role": "assistant",
  "timestamp": "2025-11-05T13:00:00Z"
}
```

**Error Response (shop not registered yet):**
```json
{
  "shop": "hearnshobbies.myshopify.com",
  "error": "Shop not found or inactive"
}
```

---

## Troubleshooting

### Issue 1: "Shop not found or inactive"

**Cause**: Shop not registered in database

**Fix**:
1. Verify `SHOPIFY_ACCESS_TOKEN` environment variable is set on Railway
2. Redeploy Railway to run migration: Click **Deploy** → **Redeploy**
3. Wait 2-3 minutes for deployment
4. Test again with curl command above

**Check Migration Ran:**
```bash
# On Railway, check logs
railway logs --tail 100
```

Look for:
```
Flyway: Migrating schema to version V009 - insert hearns hobbies shop
```

### Issue 2: CORS Error in Browser Console

**Error Message:**
```
Access to fetch at 'https://...' from origin 'https://hearnshobbies.com'
has been blocked by CORS policy
```

**Cause**: Storefront domain not in CORS whitelist

**Fix**: The backend already allows:
- `https://hearnshobbies.com` ✅
- `https://www.hearnshobbies.com` ✅
- `https://*.myshopify.com` ✅

If using a different domain (e.g., custom domain), add it to `CorsConfig.java`:

```java
.allowedOriginPatterns(
    "https://hearnshobbies.com",
    "https://www.hearnshobbies.com",
    "https://your-custom-domain.com",  // Add this
    // ... rest
)
```

Then commit and push to redeploy.

### Issue 3: Modal Doesn't Open

**Symptoms**: Clicking search button does nothing

**Check**:
1. **JavaScript Loaded**: View page source, look for:
   ```html
   <script src="/assets/ai-search-client.js"></script>
   ```

2. **Modal Element Exists**: Open console, run:
   ```javascript
   document.getElementById('ai-search-modal')
   ```
   Should return an element, not `null`

3. **Modal Controller Initialized**: Check console for:
   ```
   window.aiSearchModal
   ```
   Should be an object with `open()` and `close()` methods

**Fix**: Ensure `ai-search-assets.liquid` snippet is rendered in `theme.liquid`:
```liquid
{% render 'ai-search-assets' %}
```

### Issue 4: Products Not Displaying

**Symptoms**: AI responds but no product cards appear

**Check Response Format**:

Open browser Network tab, find the POST request to `/api/shopify/chat/message`, check response:

**Should Have**:
```json
{
  "response": "text...",
  "products": [  // This array is required!
    {
      "title": "Product Name",
      "handle": "product-handle",
      "price": 29.99,
      "image": "https://..."
    }
  ]
}
```

**If Missing**: The backend might not be returning products. Check:
1. ChatAgentService.java:75 - `search_products` tool is enabled
2. Products exist in Shopify with matching search terms
3. Backend has access to product data (check logs)

### Issue 5: 500 Internal Server Error

**Symptoms**: Chat returns generic error or 500 status

**Check Railway Logs**:
```bash
railway logs --tail 100
```

Common causes:
- **Anthropic API key not set**: Check `ANTHROPIC_API_KEY` env variable
- **Shopify API error**: Check `SHOPIFY_ACCESS_TOKEN` is valid
- **Database connection issue**: Check `DATABASE_URL` is correct

---

## Configuration Reference

### Environment Variables (Railway)

**Required**:
- `SHOPIFY_ACCESS_TOKEN` - Your Shopify Admin API token
- `ANTHROPIC_API_KEY` - Your Claude API key
- `DATABASE_URL` - PostgreSQL connection string (auto-set by Railway)

**Optional**:
- `SHOPIFY_SHOP_DOMAIN` - Default: `hearnshobbies.myshopify.com`
- `AI_MODEL` - Default: `claude-3-7-sonnet-20250219`
- `AI_TEMPERATURE` - Default: `0.7`
- `AI_MAX_TOKENS` - Default: `1024`

### Theme Block Settings (Shopify)

**AI Search Modal Block**:
- `api_url` (required): `https://shopify-data-api-production.up.railway.app/api/shopify/chat/message`
- `max_results` (optional): `10` (range: 3-20)
- `primary_color` (optional): `#4A90E2`
- `text_color` (optional): `#333333`

**AI Search Bar Block**:
- `position`: `header` | `fixed-bottom` | `inline`
- `auto_focus`: `true` | `false`

---

## API Reference

### Endpoint

```
POST /api/shopify/chat/message?shop={shop_domain}
```

**Headers**:
```
Content-Type: application/json
Accept: application/json
```

**Request Body**:
```json
{
  "message": "User's search query",
  "conversationHistory": [
    { "role": "user", "content": "previous message" },
    { "role": "assistant", "content": "previous response" }
  ],
  "maxResults": 10
}
```

**Response (Success - 200)**:
```json
{
  "response": "AI assistant response text",
  "products": [
    {
      "id": "product_id",
      "title": "Product Title",
      "handle": "product-handle",
      "price": 29.99,
      "image": "https://cdn.shopify.com/...",
      "vendor": "Brand Name",
      "sku": "SKU123"
    }
  ],
  "role": "assistant",
  "timestamp": "2025-11-05T13:00:00Z"
}
```

**Response (Error - 404)**:
```json
{
  "shop": "hearnshobbies.myshopify.com",
  "error": "Shop not found or inactive"
}
```

---

## Quick Start Checklist

Use this checklist to connect your theme to the backend:

- [ ] **Railway Backend Running**: Test health endpoint
- [ ] **Environment Variable Set**: `SHOPIFY_ACCESS_TOKEN` on Railway
- [ ] **Migration Ran**: Check Railway logs for V009 migration
- [ ] **Theme Extension Installed**: Verify blocks are in theme
- [ ] **API URL Updated**: Set in theme customizer settings
- [ ] **Test from Browser**: Open storefront and try search
- [ ] **Verify Response**: Check browser console for successful API call
- [ ] **Products Display**: Confirm product cards render correctly

---

## Next Steps

Once connected and working:

1. **Customize Chatbot Behavior**: Update configuration in Admin Dashboard
2. **Add More Agents**: Create specialist agents for paint, RC, customer service
3. **Configure Routing**: Set custom instructions for agent delegation
4. **Monitor Usage**: Check analytics dashboard for chat metrics
5. **Optimize Prompts**: Test different system prompts for better responses

---

## Support

**Issues?** Check:
1. This troubleshooting guide above
2. Railway logs: `railway logs`
3. Browser console: F12 → Console tab
4. Network tab: F12 → Network → Find POST request

**Contact:** Development team or create GitHub issue

---

**Document Version:** 1.0
**Last Updated:** November 5, 2025
**Deployment:** Railway Production
