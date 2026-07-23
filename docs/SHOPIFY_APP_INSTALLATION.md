# Shopify App Installation Guide

> **Model note (July 2026):** Claude model IDs in this document are historical. The runtime model is set by the `ANTHROPIC_MODEL` env var (currently `claude-sonnet-4-6`) — see CLAUDE.md.
## AI Product Search Assistant for Hearn's Hobbies

**Last Updated:** October 31, 2025
**App Type:** Custom/Private Shopify App
**Installation Method:** OAuth 2.0

---

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Step 1: Create Shopify App](#step-1-create-shopify-app)
4. [Step 2: Configure Backend](#step-2-configure-backend)
5. [Step 3: Install App via OAuth](#step-3-install-app-via-oauth)
6. [Step 4: Install Theme Extension](#step-4-install-theme-extension)
7. [Step 5: Verify Installation](#step-5-verify-installation)
8. [Step 6: Configure & Test](#step-6-configure--test)
9. [Troubleshooting](#troubleshooting)
10. [Uninstall Guide](#uninstall-guide)

---

## Overview

This installation guide covers deploying the AI Product Search Assistant to Hearn's Hobbies Shopify store. The app consists of:

- **Backend API** - Spring Boot application (already deployed to Railway)
- **OAuth Integration** - Custom Shopify app with shop-scoped configuration
- **Theme Extension** - Frontend search UI components for storefront

**Installation Type:** Private/Custom App (NOT App Store distribution)

---

## Prerequisites

### Required Access

- [ ] Shopify Partners account ([partners.shopify.com](https://partners.shopify.com))
- [ ] Admin access to hearnshobbies.myshopify.com
- [ ] Railway dashboard access (for environment variables)
- [ ] Theme code editor access (Online Store → Themes → Edit Code)

### Required Information

- [ ] Railway backend URL: `https://your-app.railway.app`
- [ ] Shopify store domain: `hearnshobbies.myshopify.com`
- [ ] Existing Shopify access token (for product API)
- [ ] Anthropic API key (for Claude AI)

---

## Step 1: Create Shopify App

### 1.1 Access Shopify Partners Dashboard

1. Go to [https://partners.shopify.com](https://partners.shopify.com)
2. Sign in with your Shopify Partners account
3. Navigate to **Apps** in the left sidebar

### 1.2 Create New App

1. Click **"Create app"**
2. Select **"Create app manually"**
3. Fill in app details:

```yaml
App name: AI Search Assistant
App type: Custom app
```

### 1.3 Configure App URLs

Navigate to **Configuration** tab and set:

```yaml
App URL: https://your-app.railway.app

Allowed redirection URL(s):
  - https://your-app.railway.app/shopify/callback

GDPR webhooks (optional):
  - Customer data request: https://your-app.railway.app/webhooks/gdpr/customer-data
  - Customer data erasure: https://your-app.railway.app/webhooks/gdpr/customer-erase
  - Shop data erasure: https://your-app.railway.app/webhooks/gdpr/shop-erase
```

### 1.4 Configure API Scopes

Navigate to **Configuration** → **API access scopes** and select:

| Scope | Purpose | Required? |
|-------|---------|-----------|
| `read_products` | Search product catalog | ✅ Yes |
| `write_script_tags` | Inject theme extension | ✅ Yes |
| `read_orders` | Order history (future) | ⚠️ Optional |
| `read_customers` | Personalization (future) | ⚠️ Optional |

**Minimum required scopes:**
```
read_products,write_script_tags
```

### 1.5 Get API Credentials

1. Navigate to **API credentials** tab
2. Copy the following credentials (you'll need these for Step 2):

```bash
API key: xxxxxxxxxxxxxxxx
API secret key: xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

⚠️ **Important:** Keep these credentials secure! Never commit to version control.

---

## Step 2: Configure Backend

### 2.1 Add Environment Variables to Railway

1. Go to Railway dashboard
2. Select your project
3. Navigate to **Variables** tab
4. Add the following environment variables:

```bash
# ===== Shopify OAuth Configuration =====
SHOPIFY_APP_API_KEY=<your-api-key-from-step-1.5>
SHOPIFY_APP_API_SECRET=<your-api-secret-from-step-1.5>
SHOPIFY_APP_SCOPES=read_products,write_script_tags
SHOPIFY_APP_REDIRECT_URI=https://your-app.railway.app/shopify/callback

# ===== Existing Variables (should already be set) =====
SHOPIFY_SHOP_URL=hearnshobbies.myshopify.com
SHOPIFY_ACCESS_TOKEN=<existing-admin-api-token>
ANTHROPIC_API_KEY=<your-anthropic-api-key>
DATABASE_URL=<railway-postgres-connection-url>
DATABASE_USERNAME=<postgres-username>
DATABASE_PASSWORD=<postgres-password>

# ===== Optional Configuration =====
SPRING_PROFILES_ACTIVE=production
PORT=8080
```

### 2.2 Verify application.yml Configuration

The `application.yml` file should have this Shopify app section:

```yaml
shopify:
  app:
    api-key: ${SHOPIFY_APP_API_KEY}
    api-secret: ${SHOPIFY_APP_API_SECRET}
    scopes: ${SHOPIFY_APP_SCOPES:read_products,write_script_tags}
    redirect-uri: ${SHOPIFY_APP_REDIRECT_URI:http://localhost:8080/shopify/callback}
    install-uri: ${SHOPIFY_APP_INSTALL_URI:http://localhost:8080/shopify/install}
    callback-uri: ${SHOPIFY_APP_CALLBACK_URI:http://localhost:8080/shopify/callback}
```

✅ This is already configured in the codebase.

### 2.3 Redeploy Backend

1. After adding environment variables, trigger a redeploy
2. Wait for deployment to complete
3. Check logs for successful startup:

```
INFO - Configuration validated successfully
INFO - Shopify Shop: hearnshobbies.myshopify.com
INFO - Environment: production
```

---

## Step 3: Install App via OAuth

### 3.1 OAuth Installation Flow

The app uses standard Shopify OAuth 2.0 flow:

```
1. Navigate to install URL
2. Redirect to Shopify authorization page
3. Shop owner approves permissions
4. Shopify redirects to callback URL with code
5. Backend exchanges code for access token
6. Backend saves shop to database
7. Redirect to admin dashboard
```

### 3.2 Initiate Installation

**Method A: Direct URL (Recommended)**

Navigate to:
```
https://your-app.railway.app/shopify/install?shop=hearnshobbies.myshopify.com
```

**Method B: Shopify Partners Dashboard**

1. Go to Partners Dashboard → Apps → AI Search Assistant
2. Click **"Select store"**
3. Choose **"hearnshobbies.myshopify.com"**
4. Click **"Install app"**

### 3.3 Authorize App

1. Review requested permissions:
   - ✅ Read products
   - ✅ Add scripts to storefront

2. Click **"Install app"**

3. Wait for redirect to admin dashboard:
```
https://your-app.railway.app/admin?shop=hearnshobbies.myshopify.com&installed=true
```

### 3.4 Verify OAuth Installation

**Check Database:**
```sql
SELECT
  shop_domain,
  shop_name,
  is_active,
  ai_enabled,
  installed_at
FROM shopify_shops
WHERE shop_domain = 'hearnshobbies.myshopify.com';
```

**Expected Result:**
```
shop_domain: hearnshobbies.myshopify.com
shop_name: Hearn's Hobbies
is_active: true
ai_enabled: true
installed_at: 2025-10-31 ...
```

**Check Railway Logs:**
```
INFO - Installing app for shop: hearnshobbies.myshopify.com
INFO - OAuth callback received for shop: hearnshobbies.myshopify.com
INFO - Successfully installed app for shop: hearnshobbies.myshopify.com
```

---

## Step 4: Install Theme Extension

### Option A: Shopify CLI (Recommended for Developers)

#### 4.1 Install Shopify CLI

```bash
npm install -g @shopify/cli @shopify/theme
```

#### 4.2 Authenticate

```bash
shopify auth login
```

Follow prompts to authenticate with Shopify.

#### 4.3 Navigate to Extension Directory

```bash
cd /Users/np/shopify-data-api/shopify-theme-extension
```

#### 4.4 Push to Theme

```bash
shopify theme push --store hearnshobbies
```

Select the theme to update (usually "Live theme" or "Unpublished theme" for testing).

#### 4.5 Verify Upload

Check that these files were uploaded:
- `sections/search-bar.liquid`
- `sections/search-modal.liquid`
- `assets/ai-search-client.js`
- `snippets/ai-search-assets.liquid`

---

### Option B: Manual Installation (Recommended for Non-Developers)

#### 4.1 Access Theme Editor

1. Go to hearnshobbies.myshopify.com admin
2. Navigate to **Online Store** → **Themes**
3. Click **"Actions"** → **"Edit code"**

#### 4.2 Upload Files

**Upload to `sections/` folder:**

1. Click **"Add a new section"**
2. Name: `search-bar`
3. Copy content from: `/shopify-theme-extension/blocks/search-bar.liquid`
4. Save

5. Click **"Add a new section"**
6. Name: `search-modal`
7. Copy content from: `/shopify-theme-extension/blocks/search-modal.liquid`
8. Save

**Upload to `assets/` folder:**

1. Click **"Add a new asset"**
2. Choose **"Create a blank file"**
3. Name: `ai-search-client.js`
4. Copy content from: `/shopify-theme-extension/assets/ai-search-client.js`
5. Save

**Upload to `snippets/` folder:**

1. Click **"Add a new snippet"**
2. Name: `ai-search-assets`
3. Copy content from: `/shopify-theme-extension/snippets/ai-search-assets.liquid`
4. Save

#### 4.3 Edit theme.liquid

1. In theme editor, open `Layout` → `theme.liquid`
2. Find the closing `</body>` tag (near the end of file)
3. Add this line **before** `</body>`:

```liquid
{% render 'ai-search-assets' %}
```

4. Save

#### 4.4 Add Blocks via Theme Customizer

1. Go to **Online Store** → **Themes** → **Customize**

**Add Search Bar:**
2. Navigate to header section
3. Click **"Add block"** or **"Add section"**
4. Find **"AI Search Bar"**
5. Add to header
6. Configure settings:
   - **Primary Color:** #4A90E2 (or match branding)
   - **Position:** `header` (or `fixed-bottom` for floating button)
   - **Auto-focus:** false (recommended)

**Add Search Modal:**
7. Click **"Add section"** (anywhere in template)
8. Find **"AI Search Modal"**
9. Add to page
10. Configure settings:
    - **API URL:** `https://your-app.railway.app/api/shopify/chat/message`
    - **Primary Color:** Match search bar
    - **Maximum Results:** 10

11. Click **"Save"**
12. Click **"Publish"** (if using live theme)

---

## Step 5: Verify Installation

### 5.1 Backend Verification

**Test OAuth Installation:**
```bash
curl "https://your-app.railway.app/api/shopify/config?shop=hearnshobbies.myshopify.com"
```

Expected response:
```json
{
  "shopDomain": "hearnshobbies.myshopify.com",
  "shopName": "Hearn's Hobbies",
  "isActive": true,
  "ai": {
    "enabled": true,
    "model": "claude-sonnet-4-5-20250929",
    "temperature": 0.7,
    "maxTokens": 4096
  },
  "installedAt": "2025-10-31T..."
}
```

**Test Chat API:**
```bash
curl -X POST "https://your-app.railway.app/api/shopify/chat/message?shop=hearnshobbies.myshopify.com" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Show me Gundam model kits under $50",
    "conversationHistory": [],
    "maxResults": 5
  }'
```

Expected response (200 OK):
```json
{
  "response": "Here are some great Gundam kits under $50...",
  "products": [...],
  "role": "assistant",
  "timestamp": "2025-10-31T..."
}
```

### 5.2 Frontend Verification

**Test on Storefront:**

1. Visit `https://hearnshobbies.com`
2. Look for AI search bar in header (or fixed button if configured)
3. **Alternative:** Press `Cmd/Ctrl + K` to open modal

**Test Search Flow:**

1. Click search bar → modal should open
2. Type: "I need hobby paints for plastic models"
3. Wait for AI response (should appear within 2-5 seconds)
4. Verify products are displayed in grid
5. Click product card → should navigate to product page

**Check Browser Console:**

Open DevTools (F12) and check for:
- ✅ No JavaScript errors
- ✅ API request to backend succeeds
- ✅ Response contains products
- ⚠️ If errors: Check API URL in theme settings

### 5.3 Database Verification

```sql
-- Check shop is installed
SELECT * FROM shopify_shops WHERE shop_domain = 'hearnshobbies.myshopify.com';

-- Check system prompts are loaded
SELECT prompt_name, is_active, version FROM system_prompts WHERE shop_id IS NULL;

-- Check analytics tracking (after test searches)
SELECT
  COUNT(*) as total_interactions,
  AVG(response_time_ms) as avg_response_time,
  COUNT(CASE WHEN was_successful = true THEN 1 END) as successful
FROM chat_analytics
WHERE shop_id = (SELECT id FROM shopify_shops WHERE shop_domain = 'hearnshobbies.myshopify.com');
```

---

## Step 6: Configure & Test

### 6.1 Access Admin Dashboard

Navigate to:
```
https://your-app.railway.app/admin?shop=hearnshobbies.myshopify.com
```

Or use the ProductSearchPage component (if React frontend is deployed).

### 6.2 Configure AI Settings

**Model Configuration:**
- Model: `claude-sonnet-4-5-20250929` (recommended)
- Temperature: `0.7` (balanced creativity/accuracy)
- Max Tokens: `4096` (sufficient for product search)

**Alternative Models:**
- `claude-opus-4-1-20250805` - Higher quality, slower, more expensive
- `claude-haiku-4-5-20251001` - Faster, cheaper, lower quality
- `claude-3-7-sonnet-20250219` - Previous generation, well-tested

### 6.3 Customize System Prompt

1. Navigate to **Prompt Management** tab
2. Select **"default_product_search"** prompt
3. Click **"Customize"**
4. Add shop-specific instructions:

```
Additional instructions for Hearn's Hobbies:
- We specialize in Gundam, military, and aircraft model kits
- Emphasize brands: Bandai, Tamiya, Revell, Hasegawa
- Mention skill levels (beginner/intermediate/expert)
- Always suggest complementary products (paint, tools)
```

5. Click **"Save"**

### 6.4 Enable Analytics

1. Navigate to **Analytics** tab
2. Verify analytics are being tracked
3. Monitor:
   - Total interactions
   - Success rate
   - Average response time
   - API costs

### 6.5 End-to-End Testing

**Test Scenarios:**

1. **Product Search:**
   - Query: "Show me beginner Gundam kits"
   - Expected: List of entry-level RG/HG Gundam kits

2. **Price Filter:**
   - Query: "I have $30, what can I get?"
   - Expected: Products under $30

3. **Complementary Products:**
   - Query: "I just bought a model kit, what else do I need?"
   - Expected: Tools, paints, glue, primer

4. **Out of Scope:**
   - Query: "Do you sell video games?"
   - Expected: Polite redirect to hobby products

5. **Technical Questions:**
   - Query: "What's the difference between RG and HG?"
   - Expected: Explanation + product examples

---

## Troubleshooting

### OAuth Installation Issues

**Error: "Invalid shop domain"**
- Ensure shop domain ends with `.myshopify.com`
- Use: `hearnshobbies.myshopify.com` (not `hearnshobbies.com`)

**Error: "HMAC verification failed"**
- Check `SHOPIFY_APP_API_SECRET` is correct
- Verify secret matches Shopify Partners Dashboard
- Check Railway logs for detailed error

**Error: "Invalid state parameter"**
- CSRF protection triggered
- Clear browser cookies and retry
- Check session is maintained during OAuth flow

**Error: "Shop not found or inactive" (after installation)**
- Verify shop was saved to database
- Check Railway logs for database errors
- Run migration if `shopify_shops` table doesn't exist

### Theme Extension Issues

**Search bar not appearing:**
1. Verify `ai-search-assets.liquid` is rendered in `theme.liquid`
2. Check blocks are added via Theme Customizer
3. Clear browser cache (Ctrl+Shift+R)
4. Check browser console for JavaScript errors

**Modal not opening:**
1. Verify `ai-search-client.js` is loaded
2. Check: `document.getElementById('ai-search-modal')` exists
3. Look for JavaScript errors in console
4. Ensure both blocks (search-bar + search-modal) are added

**API errors in modal:**
- Verify API URL in theme settings: `https://your-app.railway.app/api/shopify/chat/message`
- Check CORS is configured for `hearnshobbies.com`
- Test API directly with curl (see Step 5.1)
- Check network tab for 401/403/500 errors

**Products not displaying:**
- Verify `products` array is in API response
- Check product structure matches expected format
- Ensure product handles are valid
- Check Shopify API permissions (`read_products` scope)

### Backend API Issues

**Error: "Shop not found or inactive"**
- Run OAuth installation again (Step 3)
- Check database: shop should have `is_active = true`
- Verify shop domain parameter matches database

**Error: "AI assistant is disabled for this shop"**
- Update shop config: `UPDATE shopify_shops SET ai_enabled = true WHERE shop_domain = 'hearnshobbies.myshopify.com';`
- Or use config API to enable AI

**Slow response times (>10 seconds):**
- Check Anthropic API status
- Verify network connectivity to api.anthropic.com
- Monitor Railway logs for timeouts
- Consider using Haiku model for faster responses

**High API costs:**
- Monitor usage in Analytics dashboard
- Reduce `max_tokens` setting (e.g., 2048 instead of 4096)
- Use Haiku model instead of Opus
- Implement rate limiting (future enhancement)

---

## Uninstall Guide

### Remove Theme Extension

1. Go to Online Store → Themes → Edit Code
2. Remove from `theme.liquid`: `{% render 'ai-search-assets' %}`
3. Delete files:
   - `sections/search-bar.liquid`
   - `sections/search-modal.liquid`
   - `assets/ai-search-client.js`
   - `snippets/ai-search-assets.liquid`
4. Save changes

### Uninstall OAuth App

**Method A: From Shopify Admin**
1. Go to Settings → Apps and sales channels
2. Find "AI Search Assistant"
3. Click "Uninstall"

**Method B: Via API**
```bash
POST https://your-app.railway.app/shopify/uninstall?shop=hearnshobbies.myshopify.com
```

**Database Cleanup:**
```sql
UPDATE shopify_shops
SET is_active = false, uninstalled_at = CURRENT_TIMESTAMP
WHERE shop_domain = 'hearnshobbies.myshopify.com';
```

⚠️ Note: Uninstalling does NOT delete data. To fully remove:
```sql
DELETE FROM chat_analytics WHERE shop_id = (SELECT id FROM shopify_shops WHERE shop_domain = 'hearnshobbies.myshopify.com');
DELETE FROM shopify_shops WHERE shop_domain = 'hearnshobbies.myshopify.com';
```

---

## Important URLs Reference

| Purpose | URL |
|---------|-----|
| **OAuth Installation** | `https://your-app.railway.app/shopify/install?shop=hearnshobbies.myshopify.com` |
| **OAuth Callback** | `https://your-app.railway.app/shopify/callback` |
| **Admin Dashboard** | `https://your-app.railway.app/admin?shop=hearnshobbies.myshopify.com` |
| **Chat API** | `https://your-app.railway.app/api/shopify/chat/message?shop=hearnshobbies.myshopify.com` |
| **Shop Config API** | `https://your-app.railway.app/api/shopify/config?shop=hearnshobbies.myshopify.com` |
| **Analytics API** | `https://your-app.railway.app/api/shopify/analytics?shop=hearnshobbies.myshopify.com` |
| **Storefront** | `https://hearnshobbies.com` |
| **Shopify Admin** | `https://hearnshobbies.myshopify.com/admin` |
| **Shopify Partners** | `https://partners.shopify.com` |

---

## Support & Documentation

**Related Documentation:**
- `README.md` - Project overview
- `docs/ai-search-implementation/` - Technical specifications
- `shopify-theme-extension/README.md` - Theme extension details
- `docs/RAILWAY_DEPLOYMENT.md` - Deployment guide

**For Issues:**
- Check troubleshooting section above
- Review Railway logs for backend errors
- Check browser console for frontend errors
- Contact development team

---

## Checklist

Use this checklist to verify complete installation:

### Pre-Installation
- [ ] Shopify Partners account created
- [ ] Admin access to hearnshobbies.myshopify.com verified
- [ ] Railway backend deployed and running
- [ ] Environment variables documented

### Shopify App Setup
- [ ] App created in Partners Dashboard
- [ ] App URL and redirect URLs configured
- [ ] API scopes selected (`read_products`, `write_script_tags`)
- [ ] API credentials copied securely

### Backend Configuration
- [ ] Environment variables added to Railway
- [ ] `SHOPIFY_APP_API_KEY` set
- [ ] `SHOPIFY_APP_API_SECRET` set
- [ ] Backend redeployed successfully
- [ ] Logs show successful startup

### OAuth Installation
- [ ] Navigated to install URL
- [ ] App permissions reviewed and approved
- [ ] Redirected to admin dashboard successfully
- [ ] Shop exists in database (`shopify_shops` table)
- [ ] Shop is active (`is_active = true`)

### Theme Extension
- [ ] Theme files uploaded (manual or CLI)
- [ ] `ai-search-assets.liquid` rendered in `theme.liquid`
- [ ] Search bar block added to header
- [ ] Search modal block added to theme
- [ ] API URL configured in theme settings
- [ ] Theme published/saved

### Verification
- [ ] Search bar visible on storefront
- [ ] Modal opens when clicking search bar
- [ ] Can send test query
- [ ] Receives AI response with products
- [ ] Product cards clickable and navigate correctly
- [ ] No JavaScript errors in console
- [ ] API requests succeed (check network tab)
- [ ] Analytics tracking working

### Configuration
- [ ] Admin dashboard accessible
- [ ] AI settings configured (model, temperature, tokens)
- [ ] System prompt customized (optional)
- [ ] Analytics enabled and tracking

### Testing
- [ ] Tested product search query
- [ ] Tested price filter query
- [ ] Tested complementary product query
- [ ] Tested out-of-scope query
- [ ] Tested mobile responsiveness
- [ ] Tested keyboard shortcut (Cmd/Ctrl + K)

---

**Installation Complete!** 🎉

The AI Product Search Assistant is now live on hearnshobbies.com and ready to help customers find the perfect hobby products.

