# Simple Installation Guide - Custom App
## AI Product Search Assistant for Hearn's Hobbies

**Installation Type:** Custom App (Direct Access)
**No Partners Account Required** ✅
**Estimated Time:** 20-30 minutes

---

## Overview

This guide uses a **simple custom app** approach:
- Create app directly in Shopify admin
- Use Admin API token for direct access
- No OAuth complexity
- Perfect for single-shop deployment

**Your Backend URL:** `https://shopify-data-api-production.up.railway.app/`

---

## Step 1: Create Custom App in Shopify Admin (5 min)

### 1.1 Access Shopify Admin

1. Go to: https://hearnshobbies.myshopify.com/admin
2. Log in with admin credentials

### 1.2 Enable Custom App Development

1. Navigate to: **Settings** → **Apps and sales channels**
2. Click: **"Develop apps"** (top right corner)
3. If first time, click: **"Allow custom app development"**
4. Click: **"Create an app"**

### 1.3 Create App

**App Configuration:**
```
App name: AI Search Assistant
App developer: [Your name/email]
```

Click **"Create app"**

### 1.4 Configure API Scopes

1. Click **"Configure Admin API scopes"** tab
2. Scroll down and select these scopes:

**Required Scopes:**
- ✅ **Products** → `read_products` (Read products, variants, and collections)
- ✅ **Online Store** → `write_script_tags` (Modify online store content)

**Optional Scopes (for future features):**
- ☐ `read_orders` (Order history)
- ☐ `read_customers` (Customer personalization)

3. Click **"Save"**

### 1.5 Install App

1. Click **"Install app"** button (top right)
2. Review permissions
3. Click **"Install app"**

### 1.6 Get API Token

1. Click **"API credentials"** tab
2. Under **"Admin API access token"**:
   - Click **"Reveal token once"**
   - **COPY THE TOKEN** - you'll need it for Railway!

⚠️ **Important:** Save this token securely. You can only view it once!

**Token format:** `shpat_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`

---

## Step 2: Configure Railway Environment Variables (2 min)

### 2.1 Access Railway Dashboard

1. Go to: https://railway.com/
2. Navigate to your **shopify-data-api** project
3. Click **"Variables"** tab

### 2.2 Add/Update Variables

Add these environment variables (paste the token from Step 1.6):

```bash
# ===== Shopify Configuration =====
SHOPIFY_SHOP_URL=hearnshobbies.myshopify.com
SHOPIFY_ACCESS_TOKEN=shpat_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
SHOPIFY_API_VERSION=2025-01

# ===== Backend Configuration =====
SPRING_PROFILES_ACTIVE=production
PORT=8080

# ===== AI Configuration =====
ANTHROPIC_API_KEY=[your-existing-anthropic-key]
ANTHROPIC_MODEL=claude-3-7-sonnet-20250219

# ===== Database Configuration =====
# (These should already exist from Railway Postgres setup)
DATABASE_URL=[railway-postgres-url]
DATABASE_USERNAME=[postgres-username]
DATABASE_PASSWORD=[postgres-password]

# ===== CORS Configuration =====
CORS_ALLOWED_ORIGINS=https://hearnshobbies.com,https://www.hearnshobbies.com,https://hearnshobbies.myshopify.com
```

### 2.3 Redeploy Backend

After adding variables:
1. Railway will automatically redeploy
2. Wait for deployment to complete (~2 minutes)
3. Check logs for successful startup

**Expected in logs:**
```
INFO - Application started successfully
INFO - Shopify Shop: hearnshobbies.myshopify.com
```

---

## Step 3: Test Backend API (2 min)

Before installing the theme, let's verify the backend works:

### 3.1 Test Product Search API

Open your browser or use curl:

```bash
# Test product search
curl "https://shopify-data-api-production.up.railway.app/api/products/search?query=gundam&limit=5"
```

**Expected Response:** JSON with product data

### 3.2 Test Chat API (Optional - requires shop setup)

```bash
curl -X POST "https://shopify-data-api-production.up.railway.app/api/shopify/chat/message?shop=hearnshobbies.myshopify.com" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Show me Gundam kits under $50",
    "conversationHistory": [],
    "maxResults": 5
  }'
```

**Expected Response:** AI response with products

If you get errors, check Railway logs for details.

---

## Step 4: Install Theme Extension (15 min)

Now let's add the AI search interface to your storefront.

### Choose Installation Method:

#### **Option A: Manual Upload** (Recommended - Easier)
[See Section 4A below](#4a-manual-theme-installation)

#### **Option B: Shopify CLI** (For Developers)
[See Section 4B below](#4b-shopify-cli-installation)

---

## 4A: Manual Theme Installation

### 4A.1 Access Theme Editor

1. Go to: **Online Store** → **Themes**
2. Find your **live theme**
3. Click **"Actions"** → **"Edit code"**

### 4A.2 Create Section: Search Bar

1. In left sidebar, find **"Sections"** folder
2. Click **"Add a new section"**
3. Name it: `ai-search-bar`
4. Copy and paste this code:

```liquid
{% comment %}
  AI Product Search Bar
  Triggers the chat modal when clicked
{% endcomment %}

<div class="ai-search-bar-container" data-position="{{ section.settings.position }}">
  <div class="ai-search-bar-wrapper">
    <button
      type="button"
      class="ai-search-trigger"
      id="ai-search-trigger"
      aria-label="Open AI Product Search Assistant"
      aria-expanded="false"
    >
      <svg class="ai-search-icon" width="20" height="20" viewBox="0 0 20 20" fill="none">
        <path d="M9 17A8 8 0 1 0 9 1a8 8 0 0 0 0 16zM19 19l-4.35-4.35" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
      </svg>
      <span class="ai-search-text">Ask our AI assistant...</span>
      <svg class="ai-sparkle-icon" width="16" height="16" viewBox="0 0 16 16" fill="none">
        <path d="M8 1v14M1 8h14M3.5 3.5l9 9M12.5 3.5l-9 9" stroke="currentColor" stroke-width="1.5"/>
      </svg>
    </button>
  </div>
</div>

<style>
  .ai-search-bar-container {
    max-width: 600px;
    margin: 1rem auto;
  }
  .ai-search-trigger {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 14px 20px;
    background: white;
    border: 2px solid {{ section.settings.primary_color }};
    border-radius: 50px;
    color: {{ section.settings.text_color }};
    font-size: 16px;
    cursor: pointer;
    transition: all 0.3s ease;
    box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  }
  .ai-search-trigger:hover {
    background: {{ section.settings.primary_color }};
    color: white;
    transform: translateY(-2px);
  }
  .ai-search-icon { flex-shrink: 0; color: {{ section.settings.primary_color }}; }
  .ai-search-trigger:hover .ai-search-icon { color: white; }
  .ai-search-text { flex: 1; text-align: left; }
  .ai-sparkle-icon {
    flex-shrink: 0;
    color: {{ section.settings.primary_color }};
    animation: sparkle 2s ease-in-out infinite;
  }
  .ai-search-trigger:hover .ai-sparkle-icon { color: white; }
  @keyframes sparkle {
    0%, 100% { opacity: 1; transform: scale(1); }
    50% { opacity: 0.6; transform: scale(1.1); }
  }
</style>

<script>
  document.addEventListener('DOMContentLoaded', function() {
    const trigger = document.getElementById('ai-search-trigger');
    const modal = document.getElementById('ai-search-modal');
    if (trigger && modal) {
      trigger.addEventListener('click', function() {
        modal.style.display = 'flex';
        const input = modal.querySelector('#ai-chat-input');
        if (input) setTimeout(() => input.focus(), 100);
      });
    }
  });
</script>

{% schema %}
{
  "name": "AI Search Bar",
  "settings": [
    {
      "type": "color",
      "id": "primary_color",
      "label": "Primary Color",
      "default": "#4A90E2"
    },
    {
      "type": "color",
      "id": "text_color",
      "label": "Text Color",
      "default": "#333333"
    },
    {
      "type": "select",
      "id": "position",
      "label": "Position",
      "options": [
        { "value": "header", "label": "Header" },
        { "value": "fixed-bottom", "label": "Fixed Bottom" }
      ],
      "default": "header"
    }
  ]
}
{% endschema %}
```

5. Click **"Save"**

### 4A.3 Create Section: Search Modal

1. Click **"Add a new section"** again
2. Name it: `ai-search-modal`
3. This file is large - I'll create it in the next step

Let me know if you want me to provide the modal code, or would you prefer to use the files from `/shopify-theme-extension/blocks/` directory?

---

## Quick Path Forward

Since you're ready to install, here's what I recommend:

**Right Now:**
1. ✅ Create the custom app in Shopify admin (Steps 1.1-1.6)
2. ✅ Get the API token
3. ✅ Add token to Railway variables

**Then tell me:**
- "Got the token, added to Railway"

**And I'll provide:**
- Complete theme extension code (simplified for manual paste)
- OR help you use Shopify CLI for faster upload

**Which would you prefer for the theme installation:**
- **A)** I provide simplified code to paste directly
- **B)** We use the files from `/shopify-theme-extension/` folder via Shopify CLI

Let me know when you've completed Step 1 and gotten the API token! 🚀
