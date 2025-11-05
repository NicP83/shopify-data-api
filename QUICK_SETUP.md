# Quick Setup: Connect Shopify Theme to Railway Backend

**Status**: 🟡 Setup Required (3 steps, ~5 minutes)

---

## Current Status

✅ **Backend**: UP and running on Railway
✅ **Theme**: Installed in Shopify
❌ **Connection**: Needs configuration

---

## 3-Step Setup

### Step 1: Set Environment Variable on Railway (2 minutes)

1. Go to [Railway Dashboard](https://railway.app)
2. Open project: `shopify-data-api-production`
3. Click **Variables** tab
4. Add variable:
   ```
   SHOPIFY_ACCESS_TOKEN=shpat_xxxxxxxxxxxxxxxxxxxxxxxxxxxxx
   ```
   (Get from: Shopify Admin → Apps → [Custom App] → API credentials)

5. Click **Redeploy** or **Deploy Latest**
6. Wait 2-3 minutes for deployment

**This will automatically register your shop in the database.**

---

### Step 2: Update Shopify Theme Settings (2 minutes)

1. Go to **Shopify Admin** → **Online Store** → **Themes**
2. Click **Customize** on your active theme
3. Find **AI Search Modal** block
4. Update **Backend API URL** to:
   ```
   https://shopify-data-api-production.up.railway.app/api/shopify/chat/message
   ```
5. Click **Save**

---

### Step 3: Test It! (1 minute)

1. Open your store: `https://hearnshobbies.com`
2. Press **Cmd/Ctrl + K** (or click search button)
3. Type: **"Show me Gundam model kits"**
4. Press **Enter**

**Expected**: AI responds with product recommendations

---

## Verify Connection (Optional)

Run test script from terminal:

```bash
./scripts/test-chat-connection.sh
```

**If shop not registered yet**, you'll see:
```
✗ Shop not registered in database
Action Required: Set SHOPIFY_ACCESS_TOKEN on Railway
```

**After Railway redeploys**, you'll see:
```
✓ Backend is healthy and responding
✓ Shop is registered in database
✓ Chat endpoint is functional
```

---

## Troubleshooting

**Problem**: "Shop not found or inactive"
**Solution**: Complete Step 1 (set environment variable and redeploy)

**Problem**: Modal doesn't open
**Solution**: Verify theme extension is installed (check theme files)

**Problem**: CORS error
**Solution**: Already configured for `hearnshobbies.com` ✅

---

## Full Documentation

See: [`docs/SHOPIFY_THEME_CONNECTION_GUIDE.md`](docs/SHOPIFY_THEME_CONNECTION_GUIDE.md)

Detailed guide with:
- Complete troubleshooting steps
- API reference
- Configuration options
- Browser console debugging

---

**Need Help?** Check full guide or Railway logs: `railway logs`
