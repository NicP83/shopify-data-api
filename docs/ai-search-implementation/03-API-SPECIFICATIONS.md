# API Specifications
## AI-Enhanced Search for Hearn's Hobbies

---

## Overview

This document defines all API endpoints required for the Shopify AI-enhanced search integration. It covers OAuth flows, shop management, chat APIs, and admin endpoints.

---

## Base URLs

### Development
```
Backend API: http://localhost:8080
Frontend:    http://localhost:5173
```

### Production
```
Backend API: https://your-app.railway.app
Frontend:    https://hearnshobbies.com (Shopify theme extension)
```

---

## Authentication

### Shopify OAuth Flow

#### 1. Install App (Initiate OAuth)

**Endpoint:** `GET /shopify/install`

**Description:** Initiates Shopify OAuth flow. Merchant clicks "Install App" and is redirected here.

**Request:**
```http
GET /shopify/install?shop=hearnshobbies.myshopify.com HTTP/1.1
Host: your-app.railway.app
```

**Query Parameters:**
| Parameter | Type   | Required | Description |
|-----------|--------|----------|-------------|
| shop      | string | Yes      | Shop domain (e.g., "hearnshobbies.myshopify.com") |

**Response:**
```http
HTTP/1.1 302 Found
Location: https://hearnshobbies.myshopify.com/admin/oauth/authorize?
  client_id=YOUR_API_KEY&
  scope=read_products,write_script_tags&
  redirect_uri=https://your-app.railway.app/shopify/callback&
  state=RANDOM_NONCE&
  grant_options[]=value
```

**Security:**
- Validates shop domain format
- Generates random nonce (stored in session/cache)
- HTTPS required in production

---

#### 2. OAuth Callback

**Endpoint:** `GET /shopify/callback`

**Description:** Shopify redirects here after merchant approves app installation.

**Request:**
```http
GET /shopify/callback?
  shop=hearnshobbies.myshopify.com&
  code=AUTHORIZATION_CODE&
  state=RANDOM_NONCE&
  hmac=HMAC_SIGNATURE&
  timestamp=1234567890 HTTP/1.1
Host: your-app.railway.app
```

**Query Parameters:**
| Parameter | Type   | Required | Description |
|-----------|--------|----------|-------------|
| shop      | string | Yes      | Shop domain |
| code      | string | Yes      | Authorization code (exchange for token) |
| state     | string | Yes      | Nonce from install step |
| hmac      | string | Yes      | HMAC signature for verification |
| timestamp | string | Yes      | Request timestamp |

**Response:**
```http
HTTP/1.1 302 Found
Location: https://your-app.railway.app/admin?shop=hearnshobbies.myshopify.com
```

**Backend Process:**
1. Verify HMAC signature
2. Validate nonce matches session
3. Exchange code for access token
4. Save shop + token to database
5. Redirect to admin dashboard

**Error Response:**
```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": "Invalid HMAC signature",
  "message": "Request signature verification failed"
}
```

---

### CORS Configuration

All API endpoints must allow requests from Shopify storefronts:

```
Access-Control-Allow-Origin: https://hearnshobbies.com
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type, Authorization
Access-Control-Allow-Credentials: true
```

---

## Shop Management APIs

### Get Shop Configuration

**Endpoint:** `GET /api/shopify/shops/{shopDomain}`

**Description:** Get shop details and AI configuration.

**Request:**
```http
GET /api/shopify/shops/hearnshobbies.myshopify.com HTTP/1.1
Host: your-app.railway.app
```

**Response:**
```json
{
  "id": 1,
  "shopDomain": "hearnshobbies.myshopify.com",
  "shopName": "Hearn's Hobbies",
  "shopEmail": "info@hearnshobbies.com",
  "planName": "shopify",
  "currency": "USD",
  "timezone": "America/New_York",
  "aiConfig": {
    "enabled": true,
    "model": "claude-sonnet-4-5-20250929",
    "temperature": 0.7,
    "maxTokens": 4096,
    "systemPrompt": null
  },
  "analytics": {
    "enabled": true,
    "trackChatUsage": true
  },
  "installedAt": "2025-10-30T10:00:00Z",
  "isActive": true
}
```

**Status Codes:**
- `200 OK` - Shop found
- `404 Not Found` - Shop not installed
- `500 Internal Server Error` - Database error

---

### Update Shop AI Configuration

**Endpoint:** `PUT /api/shopify/shops/{shopDomain}/config`

**Description:** Update AI configuration for a shop.

**Request:**
```http
PUT /api/shopify/shops/hearnshobbies.myshopify.com/config HTTP/1.1
Host: your-app.railway.app
Content-Type: application/json

{
  "aiConfig": {
    "enabled": true,
    "model": "claude-opus-4-1-20250805",
    "temperature": 0.8,
    "maxTokens": 8192,
    "systemPrompt": "You are a hobby expert specializing in model kits..."
  }
}
```

**Response:**
```json
{
  "success": true,
  "message": "Configuration updated successfully",
  "config": {
    "enabled": true,
    "model": "claude-opus-4-1-20250805",
    "temperature": 0.8,
    "maxTokens": 8192,
    "systemPrompt": "You are a hobby expert specializing in model kits..."
  }
}
```

**Status Codes:**
- `200 OK` - Updated successfully
- `400 Bad Request` - Invalid configuration
- `404 Not Found` - Shop not found
- `500 Internal Server Error` - Update failed

---

### List All Shops (Admin)

**Endpoint:** `GET /api/shopify/shops`

**Description:** Get all installed shops (admin only).

**Request:**
```http
GET /api/shopify/shops HTTP/1.1
Host: your-app.railway.app
Authorization: Bearer ADMIN_TOKEN
```

**Response:**
```json
{
  "shops": [
    {
      "shopDomain": "hearnshobbies.myshopify.com",
      "shopName": "Hearn's Hobbies",
      "planName": "shopify",
      "installedAt": "2025-10-30T10:00:00Z",
      "isActive": true,
      "aiEnabled": true
    },
    {
      "shopDomain": "another-shop.myshopify.com",
      "shopName": "Another Shop",
      "planName": "basic",
      "installedAt": "2025-10-29T14:30:00Z",
      "isActive": true,
      "aiEnabled": false
    }
  ],
  "total": 2
}
```

---

## Chat APIs

### Send Chat Message (Shop-Scoped)

**Endpoint:** `POST /api/shopify/chat/message`

**Description:** Send a message to the AI assistant for a specific shop.

**Request:**
```http
POST /api/shopify/chat/message?shop=hearnshobbies.myshopify.com HTTP/1.1
Host: your-app.railway.app
Content-Type: application/json

{
  "message": "I'm looking for Gundam model kits for beginners",
  "conversationHistory": []
}
```

**Request Body:**
| Field               | Type   | Required | Description |
|---------------------|--------|----------|-------------|
| message             | string | Yes      | User's message |
| conversationHistory | array  | No       | Previous messages (empty for new conversation) |

**Response:**
```json
{
  "response": "I'd be happy to help you find beginner-friendly Gundam kits! Here are my top recommendations:\n\n**1. RG RX-78-2 Gundam** - $28.99\nThe classic Gundam with excellent detail and articulation. Great for learning panel lining.\n\n**2. HG Barbatos Lupus** - $19.99\nFrom Iron-Blooded Orphans series. Simpler build, perfect for first-timers.\n\n**3. SD Cross Silhouette RX-78-2** - $14.99\nSuper-deformed style, very beginner-friendly with fewer parts.\n\nAll three come with clear instructions and don't require glue. Would you like more details on any of these?",
  "products": [
    {
      "id": "gid://shopify/Product/123456",
      "title": "RG RX-78-2 Gundam",
      "handle": "rg-rx-78-2-gundam",
      "price": "28.99",
      "currency": "USD",
      "image": "https://cdn.shopify.com/s/files/1/.../gundam.jpg",
      "url": "https://hearnshobbies.com/products/rg-rx-78-2-gundam",
      "inStock": true,
      "vendor": "Bandai",
      "tags": ["gundam", "real-grade", "beginner"]
    },
    {
      "id": "gid://shopify/Product/123457",
      "title": "HG Barbatos Lupus",
      "handle": "hg-barbatos-lupus",
      "price": "19.99",
      "currency": "USD",
      "image": "https://cdn.shopify.com/s/files/1/.../barbatos.jpg",
      "url": "https://hearnshobbies.com/products/hg-barbatos-lupus",
      "inStock": true,
      "vendor": "Bandai",
      "tags": ["gundam", "high-grade", "IBO"]
    }
  ],
  "conversationId": "conv_abc123",
  "timestamp": "2025-10-30T15:30:00Z"
}
```

**Status Codes:**
- `200 OK` - Message processed successfully
- `400 Bad Request` - Invalid request (missing shop, empty message)
- `404 Not Found` - Shop not found or not active
- `429 Too Many Requests` - Rate limit exceeded
- `500 Internal Server Error` - AI service error
- `503 Service Unavailable` - AI service timeout

**Error Response:**
```json
{
  "error": "Shop not found",
  "message": "hearnshobbies.myshopify.com is not installed or inactive",
  "code": "SHOP_NOT_FOUND"
}
```

---

### Get Conversation History

**Endpoint:** `GET /api/shopify/chat/history/{conversationId}`

**Description:** Retrieve previous conversation messages.

**Request:**
```http
GET /api/shopify/chat/history/conv_abc123?shop=hearnshobbies.myshopify.com HTTP/1.1
Host: your-app.railway.app
```

**Response:**
```json
{
  "conversationId": "conv_abc123",
  "shop": "hearnshobbies.myshopify.com",
  "messages": [
    {
      "role": "user",
      "content": "I'm looking for Gundam model kits",
      "timestamp": "2025-10-30T15:30:00Z"
    },
    {
      "role": "assistant",
      "content": "I'd be happy to help! What's your experience level?",
      "timestamp": "2025-10-30T15:30:05Z"
    },
    {
      "role": "user",
      "content": "Complete beginner",
      "timestamp": "2025-10-30T15:30:15Z"
    }
  ],
  "createdAt": "2025-10-30T15:30:00Z",
  "updatedAt": "2025-10-30T15:30:15Z"
}
```

---

## Product Search APIs

### Search Products (Direct)

**Endpoint:** `POST /api/products/search`

**Description:** Direct product search without AI (used by admin quick search).

**Request:**
```http
POST /api/products/search HTTP/1.1
Host: your-app.railway.app
Content-Type: application/json

{
  "query": "gundam",
  "limit": 10,
  "offset": 0
}
```

**Response:**
```json
{
  "products": [
    {
      "id": "gid://shopify/Product/123456",
      "title": "RG RX-78-2 Gundam",
      "handle": "rg-rx-78-2-gundam",
      "description": "1/144 scale Real Grade model kit...",
      "price": "28.99",
      "compareAtPrice": "34.99",
      "currency": "USD",
      "image": "https://cdn.shopify.com/s/files/1/.../gundam.jpg",
      "images": [
        "https://cdn.shopify.com/s/files/1/.../gundam-1.jpg",
        "https://cdn.shopify.com/s/files/1/.../gundam-2.jpg"
      ],
      "url": "https://hearnshobbies.com/products/rg-rx-78-2-gundam",
      "inStock": true,
      "inventory": 5,
      "vendor": "Bandai",
      "productType": "Model Kits",
      "tags": ["gundam", "real-grade", "beginner"],
      "variants": [
        {
          "id": "gid://shopify/ProductVariant/789",
          "title": "Default",
          "price": "28.99",
          "inStock": true,
          "inventory": 5
        }
      ]
    }
  ],
  "total": 42,
  "limit": 10,
  "offset": 0,
  "hasMore": true
}
```

**Status Codes:**
- `200 OK` - Search completed
- `400 Bad Request` - Invalid query
- `500 Internal Server Error` - Shopify API error

---

## Configuration APIs

### Get Available Models

**Endpoint:** `GET /api/config/models`

**Description:** Get list of available Claude models for AI configuration.

**Request:**
```http
GET /api/config/models HTTP/1.1
Host: your-app.railway.app
```

**Response:**
```json
{
  "models": [
    "claude-sonnet-4-5-20250929",
    "claude-opus-4-1-20250805",
    "claude-haiku-4-5-20251001",
    "claude-sonnet-4-20250514",
    "claude-opus-4-20250514",
    "claude-3-7-sonnet-20250219",
    "claude-3-5-haiku-20241022",
    "claude-3-opus-20240229",
    "claude-3-sonnet-20240229",
    "claude-3-haiku-20240307"
  ],
  "current": "claude-sonnet-4-5-20250929",
  "recommended": "claude-sonnet-4-5-20250929"
}
```

---

### Get System Prompt Preview

**Endpoint:** `GET /api/config/chatbot/preview-prompt`

**Description:** Preview the system prompt that will be sent to Claude API.

**Request:**
```http
GET /api/config/chatbot/preview-prompt?shop=hearnshobbies.myshopify.com HTTP/1.1
Host: your-app.railway.app
```

**Response:**
```json
{
  "prompt": "You are a knowledgeable hobby store assistant for Hearn's Hobbies...\n\nWhen recommending products:\n1. Always use the search_products tool\n2. Provide 2-4 relevant options\n3. Include price, availability, skill level\n4. Format product cards with images\n5. Be enthusiastic about the hobby\n\nShop details:\n- Name: Hearn's Hobbies\n- Currency: USD\n- Specialties: Model kits, RC vehicles, craft supplies",
  "config": {
    "model": "claude-sonnet-4-5-20250929",
    "temperature": 0.7,
    "maxTokens": 4096
  },
  "message": "This prompt will be sent to Claude API based on current configuration"
}
```

---

## Analytics APIs (Future Enhancement)

### Track Chat Usage

**Endpoint:** `POST /api/analytics/chat-usage`

**Description:** Track chat interactions for analytics.

**Request:**
```http
POST /api/analytics/chat-usage HTTP/1.1
Host: your-app.railway.app
Content-Type: application/json

{
  "shop": "hearnshobbies.myshopify.com",
  "conversationId": "conv_abc123",
  "messageCount": 3,
  "productsShown": 2,
  "clickedProducts": ["gid://shopify/Product/123456"],
  "addedToCart": [],
  "timestamp": "2025-10-30T15:30:00Z"
}
```

**Response:**
```json
{
  "success": true,
  "tracked": true
}
```

---

## Rate Limiting

### Limits by Endpoint

| Endpoint                     | Limit          | Window  |
|------------------------------|----------------|---------|
| `/shopify/install`           | 10 per IP      | 1 hour  |
| `/shopify/callback`          | 5 per IP       | 5 min   |
| `/api/shopify/chat/message`  | 100 per shop   | 1 hour  |
| `/api/products/search`       | 200 per shop   | 1 hour  |
| `/api/config/*`              | 50 per shop    | 1 hour  |

### Rate Limit Response

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: 3600

{
  "error": "Rate limit exceeded",
  "message": "You have exceeded 100 requests per hour",
  "retryAfter": 3600,
  "limit": 100,
  "remaining": 0,
  "resetAt": "2025-10-30T16:30:00Z"
}
```

---

## Error Codes Reference

### Standard Error Response Format

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable error description",
  "details": {
    "field": "validation error details"
  },
  "timestamp": "2025-10-30T15:30:00Z",
  "path": "/api/shopify/chat/message"
}
```

### Common Error Codes

| Code                    | HTTP Status | Description |
|-------------------------|-------------|-------------|
| `SHOP_NOT_FOUND`        | 404         | Shop not installed or inactive |
| `INVALID_SHOP_DOMAIN`   | 400         | Shop domain format invalid |
| `INVALID_HMAC`          | 401         | HMAC signature verification failed |
| `OAUTH_FAILED`          | 500         | OAuth token exchange failed |
| `AI_SERVICE_ERROR`      | 500         | Claude API error |
| `AI_SERVICE_TIMEOUT`    | 503         | Claude API timeout (> 30s) |
| `PRODUCT_SEARCH_FAILED` | 500         | Shopify product search failed |
| `RATE_LIMIT_EXCEEDED`   | 429         | Too many requests |
| `INVALID_REQUEST`       | 400         | Missing required fields |
| `DATABASE_ERROR`        | 500         | Database operation failed |

---

## Testing Endpoints

### Health Check

**Endpoint:** `GET /health`

**Response:**
```json
{
  "status": "UP",
  "timestamp": "2025-10-30T15:30:00Z",
  "services": {
    "database": "UP",
    "shopify": "UP",
    "claude": "UP"
  }
}
```

### Version Info

**Endpoint:** `GET /api/version`

**Response:**
```json
{
  "version": "1.0.0",
  "buildDate": "2025-10-30",
  "commit": "abc123def456",
  "environment": "production"
}
```

---

## Postman Collection

**Import this collection for testing:**

```json
{
  "info": {
    "name": "Hearn's Hobbies AI Search API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "OAuth Flow",
      "item": [
        {
          "name": "Install App",
          "request": {
            "method": "GET",
            "url": "{{base_url}}/shopify/install?shop=hearnshobbies.myshopify.com"
          }
        }
      ]
    },
    {
      "name": "Chat API",
      "item": [
        {
          "name": "Send Message",
          "request": {
            "method": "POST",
            "url": "{{base_url}}/api/shopify/chat/message?shop=hearnshobbies.myshopify.com",
            "body": {
              "mode": "raw",
              "raw": "{\"message\": \"Looking for Gundam kits\"}",
              "options": {
                "raw": {
                  "language": "json"
                }
              }
            }
          }
        }
      ]
    }
  ],
  "variable": [
    {
      "key": "base_url",
      "value": "http://localhost:8080"
    }
  ]
}
```

---

## Related Documentation

- **01-ARCHITECTURE.md** - System architecture and data flow
- **02-DATABASE-SCHEMA.md** - Database design
- **04-PHASE1-BACKEND.md** - Backend implementation guide
- **10-CONFIGURATION.md** - Environment variables

---

*Last Updated: 2025-10-30*
*Next: 04-PHASE1-BACKEND.md*
