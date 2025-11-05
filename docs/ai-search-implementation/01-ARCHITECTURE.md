# Technical Architecture
## Hearn's Hobbies AI-Enhanced Search System

---

## 📋 Table of Contents
1. [High-Level Architecture](#high-level-architecture)
2. [System Components](#system-components)
3. [Data Flow](#data-flow)
4. [Security Architecture](#security-architecture)
5. [Deployment Architecture](#deployment-architecture)
6. [Component Dependencies](#component-dependencies)
7. [Performance Considerations](#performance-considerations)

---

## 1. High-Level Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    CUSTOMER BROWSER                         │
│                                                             │
│  hearnshobbies.com (Shopify Storefront)                    │
│  ┌───────────────────────────────────────────────┐         │
│  │  HEADER                                        │         │
│  │  ┌──────────────────────────┐  ┌────┐ ┌────┐ │         │
│  │  │ [Search products...    ] │  │ 🔍 │ │ 🤖 │ │         │
│  │  └──────────────────────────┘  └────┘ └────┘ │         │
│  └───────────────────────────────────────────────┘         │
│         │                                  │                │
│         │ Click 🔍                        │ Click 🤖       │
│         │ (Shopify Native)                │ (Custom AI)    │
│         ▼                                  ▼                │
│  ┌─────────────────┐          ┌─────────────────────┐     │
│  │ Shopify Native  │          │  AI Chat Modal      │     │
│  │ Predictive      │          │  (JavaScript)       │     │
│  │ Search          │          │  ┌───────────────┐  │     │
│  │ (< 100ms)       │          │  │ 🤖 Messages   │  │     │
│  │                 │          │  │ [Product Card]│  │     │
│  └─────────────────┘          │  │ [Product Card]│  │     │
│                                │  └───────────────┘  │     │
│                                │  [Input: Ask AI...] │     │
│                                └─────────────────────┘     │
└───────────────────────────────────┼─────────────────────────┘
                                    │
                                    │ HTTPS POST
                                    │ /api/shopify/chat/message
                                    ▼
┌─────────────────────────────────────────────────────────────┐
│               YOUR SPRING BOOT BACKEND                      │
│                   (Railway Hosting)                         │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  API Layer (Controllers)                             │  │
│  │  ├─ ShopifyInstallController (@RestController)      │  │
│  │  │  └─ GET  /api/shopify/install                    │  │
│  │  │  └─ GET  /api/shopify/callback                   │  │
│  │  │                                                   │  │
│  │  ├─ ShopifyChatController (@RestController)         │  │
│  │  │  └─ POST /api/shopify/chat/message               │  │
│  │  │  └─ GET  /api/shopify/chat/config                │  │
│  │  │                                                   │  │
│  │  └─ ConfigController (existing, enhanced)           │  │
│  └──────────────────────────────────────────────────────┘  │
│                         │                                   │
│                         ▼                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Service Layer                                       │  │
│  │  ├─ ChatAgentService (@Service)                     │  │
│  │  │  └─ processChat(ChatRequest): Mono<ChatMessage> │  │
│  │  │  └─ buildSystemPrompt(): String                  │  │
│  │  │  └─ executeToolCallReactive(): Mono<String>     │  │
│  │  │                                                   │  │
│  │  ├─ ProductService (@Service)                       │  │
│  │  │  └─ searchProductsReactive(): Mono<Map>         │  │
│  │  │  └─ 3-level fallback strategy                    │  │
│  │  │                                                   │  │
│  │  ├─ ShopifyShopService (@Service)                   │  │
│  │  │  └─ getShop(domain): ShopifyShop                 │  │
│  │  │  └─ saveShop(): ShopifyShop                      │  │
│  │  │                                                   │  │
│  │  └─ ChatbotConfigService (@Service)                 │  │
│  └──────────────────────────────────────────────────────┘  │
│                         │                                   │
│                         ▼                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Data Layer                                          │  │
│  │  ├─ ShopifyShopRepository (JpaRepository)           │  │
│  │  └─ PostgreSQL Database                             │  │
│  │     └─ shopify_shops table                          │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  External API Clients                                │  │
│  │  ├─ WebClient → Anthropic Claude API                │  │
│  │  └─ ShopifyGraphQLClient → Shopify Admin API        │  │
│  └──────────────────────────────────────────────────────┘  │
└───────────────────────┬───────────────┬─────────────────────┘
                        │               │
                        ▼               ▼
            ┌───────────────────┐  ┌────────────────────┐
            │  Shopify Admin    │  │  Anthropic Claude  │
            │  GraphQL API      │  │  API               │
            │  - Products       │  │  - Messages API    │
            │  - Variants       │  │  - Tool Use        │
            │  - Images         │  │  - Streaming       │
            └───────────────────┘  └────────────────────┘
```

### Architecture Layers

#### **1. Presentation Layer**
- **Customer UI:** Shopify theme with JavaScript widget
- **Admin UI:** React application (internal tool)
- **Responsibilities:**
  - Render UI components
  - Handle user interactions
  - Make API calls
  - Display responses

#### **2. API Layer**
- **Technology:** Spring Boot @RestController
- **Endpoints:** RESTful HTTP APIs
- **Responsibilities:**
  - Request validation
  - Authentication/authorization
  - Route to appropriate services
  - Format responses

#### **3. Service Layer**
- **Technology:** Spring @Service beans
- **Pattern:** Business logic separation
- **Responsibilities:**
  - Core business logic
  - External API orchestration
  - Data transformation
  - Complex operations

#### **4. Data Layer**
- **Technology:** Spring Data JPA
- **Database:** PostgreSQL
- **Responsibilities:**
  - Data persistence
  - Query execution
  - Transaction management

#### **5. Integration Layer**
- **Technology:** WebClient (reactive)
- **APIs:** Shopify, Claude
- **Responsibilities:**
  - External API calls
  - Response parsing
  - Error handling
  - Rate limiting

---

## 2. System Components

### Backend Components

```
backend/
├── controllers/
│   ├── ShopifyInstallController.java
│   │   └─ Handles OAuth flow (install/callback)
│   │
│   ├── ShopifyChatController.java
│   │   └─ Handles customer chat messages
│   │
│   └── ConfigController.java (existing)
│       └─ Admin configuration endpoints
│
├── services/
│   ├── ChatAgentService.java (enhanced)
│   │   ├─ processChat(ChatRequest)
│   │   ├─ buildSystemPrompt()
│   │   ├─ callClaudeWithTools()
│   │   └─ executeToolCallReactive()
│   │
│   ├── ProductService.java (existing)
│   │   ├─ searchProductsReactive()
│   │   └─ 3-level fallback search
│   │
│   ├── ShopifyShopService.java (new)
│   │   ├─ getShop(domain)
│   │   ├─ saveShop()
│   │   └─ updateConfig()
│   │
│   ├── ShopifyOAuthService.java (new)
│   │   ├─ generateAuthUrl()
│   │   ├─ exchangeCodeForToken()
│   │   └─ verifyHmac()
│   │
│   └── ChatbotConfigService.java (existing)
│       └─ Dynamic configuration
│
├── models/
│   ├── ShopifyShop.java (new)
│   │   └─ Shop data + config
│   │
│   ├── ChatMessage.java (existing)
│   ├── ChatRequest.java (existing)
│   └── ChatbotConfig.java (existing)
│
├── repositories/
│   └── ShopifyShopRepository.java (new)
│       └─ JpaRepository<ShopifyShop, Long>
│
└── config/
    ├── ShopifyAppConfig.java (new)
    │   └─ OAuth credentials
    │
    └── WebConfig.java (update)
        └─ CORS configuration
```

### Frontend Components

```
frontend/
├── pages/
│   ├── ProductSearchAssistant.jsx (new)
│   │   └─ Dual-mode search page
│   │
│   ├── ChatAgent.jsx (existing)
│   │   └─ Full chat interface
│   │
│   └── Settings.jsx (existing)
│       └─ Admin settings
│
├── components/
│   ├── ChatProductCard.jsx (new)
│   │   └─ Compact product card for chat
│   │
│   ├── SearchModeToggle.jsx (new)
│   │   └─ Quick/AI mode toggle
│   │
│   ├── ChatInterface.jsx (new)
│   │   └─ Embeddable chat interface
│   │
│   ├── ChatMessageBubble.jsx (update)
│   │   └─ Add product rendering
│   │
│   └── ProductCard.jsx (existing)
│       └─ Standard product card
│
└── utils/
    └── ResponseParser.js (new)
        └─ Parse Claude responses
```

### Shopify Theme Extension

```
extensions/ai-search-enhancement/
├── blocks/
│   └── search-bar-enhancer.liquid
│       └─ Liquid template for theme editor
│
├── assets/
│   ├── search-enhancer.js
│   │   └─ Main widget JavaScript
│   │
│   └── ai-chat-styles.css
│       └─ Widget styles
│
└── shopify.extension.toml
    └─ Extension configuration
```

---

## 3. Data Flow

### 3.1 OAuth Installation Flow

```
┌──────────┐                                    ┌─────────────────┐
│ Merchant │                                    │ Shopify Partner │
│ (Admin)  │                                    │ Dashboard       │
└────┬─────┘                                    └────────┬────────┘
     │                                                    │
     │ 1. Click "Install App"                            │
     │───────────────────────────────────────────────────▶
     │                                                    │
     │                  ┌──────────────┐                 │
     │                  │ Your Backend │                 │
     │                  └──────┬───────┘                 │
     │                         │                         │
     │ 2. GET /api/shopify/install?shop=hearns...        │
     │◀────────────────────────┤                         │
     │                         │                         │
     │ 3. Generate OAuth URL   │                         │
     │ (API key, scopes,       │                         │
     │  redirect_uri, nonce)   │                         │
     │                         │                         │
     │ 4. 302 Redirect to      │                         │
     │    Shopify OAuth        │                         │
     │─────────────────────────┼────────────────────────▶
     │                         │                         │
     │ 5. OAuth Consent Screen │                         │
     │    "Allow app to..."    │                         │
     │◀────────────────────────┼─────────────────────────│
     │                         │                         │
     │ 6. Merchant clicks      │                         │
     │    "Install app"        │                         │
     │─────────────────────────┼────────────────────────▶│
     │                         │                         │
     │ 7. Shopify redirects    │                         │
     │    to callback with     │                         │
     │    code + hmac          │                         │
     │─────────────────────────▶                         │
     │                         │                         │
     │                    8. Verify HMAC                  │
     │                    9. Verify nonce                 │
     │                   10. POST to Shopify              │
     │                       /oauth/access_token          │
     │                       with code                    │
     │                    ──────────────────▶             │
     │                    ◀──────────────────             │
     │                    (access_token)                  │
     │                         │                         │
     │                   11. Save to DB                   │
     │                       shopify_shops table          │
     │                       (shop, token, config)        │
     │                         │                         │
     │ 12. 302 Redirect to     │                         │
     │     app admin page      │                         │
     │◀────────────────────────┤                         │
     │                         │                         │
     │ 13. Access admin UI     │                         │
     │     (configure settings)│                         │
```

### 3.2 Customer Chat Flow (Detailed)

```
┌──────────┐        ┌──────────────┐        ┌─────────────┐        ┌──────────┐
│ Customer │        │ AI Widget    │        │ Backend API │        │ Claude   │
│ Browser  │        │ (JavaScript) │        │ (Spring)    │        │ API      │
└────┬─────┘        └──────┬───────┘        └──────┬──────┘        └────┬─────┘
     │                     │                       │                     │
     │ 1. Click 🤖 button │                       │                     │
     │────────────────────▶                       │                     │
     │                     │                       │                     │
     │ 2. Open modal       │                       │                     │
     │     (with animation)│                       │                     │
     │◀────────────────────┤                       │                     │
     │                     │                       │                     │
     │ 3. Type: "Do you    │                       │                     │
     │    have white paint │                       │                     │
     │    for Gundam?"     │                       │                     │
     │────────────────────▶                       │                     │
     │                     │                       │                     │
     │                4. Show "Sending..."         │                     │
     │                     │                       │                     │
     │                5. POST /api/shopify/chat/   │                     │
     │                   message?shop=hearns...    │                     │
     │                   {                         │                     │
     │                     message: "Do you...",   │                     │
     │                     conversationHistory: [] │                     │
     │                   }                         │                     │
     │                     ├──────────────────────▶│                     │
     │                     │                       │                     │
     │                     │                  6. Get shop config         │
     │                     │                     from database           │
     │                     │                     (ShopifyShopService)    │
     │                     │                       │                     │
     │                     │                  7. Build system prompt     │
     │                     │                     (ChatAgentService)      │
     │                     │                       │                     │
     │                     │                  8. POST /v1/messages       │
     │                     │                     {                       │
     │                     │                       model: "claude-...",  │
     │                     │                       system: "You are...", │
     │                     │                       messages: [...],      │
     │                     │                       tools: [search_prods] │
     │                     │                     }                       │
     │                     │                       ├────────────────────▶│
     │                     │                       │                     │
     │                     │                       │              9. Claude analyzes
     │                     │                       │                 decides to use
     │                     │                       │                 search_products
     │                     │                       │                 tool            │
     │                     │                       │                     │
     │                     │                       │          10. Response: tool_use
     │                     │                       │              {                  │
     │                     │                       │                stop_reason:     │
     │                     │                       │                  "tool_use",    │
     │                     │                       │                content: [{      │
     │                     │                       │                  type: "tool_use"│
     │                     │                       │                  name: "search.."│
     │                     │                       │                  input: {       │
     │                     │                       │                    query: "white│
     │                     │                       │                           paint"│
     │                     │                       │                  }              │
     │                     │                       │                }]               │
     │                     │                       │              }                  │
     │                     │                       │◀────────────────────│
     │                     │                       │                     │
     │                     │                 11. Execute tool:           │
     │                     │                     ProductService          │
     │                     │                     .searchProductsReactive(│
     │                     │                       "white paint", 5)     │
     │                     │                       │                     │
     │                     │                       ▼                     │
     │                     │                  ┌─────────────┐           │
     │                     │                  │   Shopify   │           │
     │                     │                  │  GraphQL    │           │
     │                     │                  │   Search    │           │
     │                     │                  └──────┬──────┘           │
     │                     │                         │                  │
     │                     │                  12. Products: [           │
     │                     │                       {                    │
     │                     │                         id: "...",          │
     │                     │                         title: "Mr. Color..│
     │                     │                         price: "4.99",     │
     │                     │                         ...                │
     │                     │                       }                    │
     │                     │                     ]                      │
     │                     │                       ◀──                  │
     │                     │                       │                    │
     │                     │                 13. Format as JSON         │
     │                     │                     Return to Claude       │
     │                     │                       │                    │
     │                     │                 14. Continue conversation  │
     │                     │                     POST /v1/messages      │
     │                     │                     {                      │
     │                     │                       messages: [          │
     │                     │                         ...previous,       │
     │                     │                         {role: "assistant",│
     │                     │                          content: tool_use}│
     │                     │                         {role: "user",     │
     │                     │                          content: [        │
     │                     │                            {               │
     │                     │                              type:         │
     │                     │                                "tool_result│
     │                     │                              tool_use_id:..│
     │                     │                              content: "[...]│
     │                     │                            }               │
     │                     │                          ]}                │
     │                     │                       ]                    │
     │                     │                     }                      │
     │                     │                       ├────────────────────▶
     │                     │                       │                    │
     │                     │                       │          15. Claude formats
     │                     │                       │              friendly response
     │                     │                       │              with products in
     │                     │                       │              JSON code block   │
     │                     │                       │                    │
     │                     │                       │          16. Response:         │
     │                     │                       │              {                 │
     │                     │                       │                stop_reason:    │
     │                     │                       │                  "end_turn",   │
     │                     │                       │                content: [{     │
     │                     │                       │                  type: "text", │
     │                     │                       │                  text: "I found│
     │                     │                       │                    3 white paint│
     │                     │                       │                    options!\n\n │
     │                     │                       │                    ```json\n   │
     │                     │                       │                    [{...}]\n   │
     │                     │                       │                    ```"        │
     │                     │                       │                }]              │
     │                     │                       │              }                 │
     │                     │                       │◀────────────────────│
     │                     │                       │                     │
     │                     │                 17. Extract text + products│
     │                     │                     from response          │
     │                     │                       │                    │
     │                     │                 18. Return to frontend     │
     │                     │◀──────────────────────┤                    │
     │                     │   {                   │                    │
     │                     │     success: true,    │                    │
     │                     │     data: {           │                    │
     │                     │       role: "assistant"                    │
     │                     │       content: "..."  │                    │
     │                     │     }                 │                    │
     │                     │   }                   │                    │
     │                     │                       │                    │
     │                19. Parse response:          │                    │
     │                    - Extract text           │                    │
     │                    - Extract JSON products  │                    │
     │                    - Render product cards   │                    │
     │                     │                       │                    │
     │ 20. Display in chat:│                       │                    │
     │     "I found 3..."  │                       │                    │
     │     [Product Card 1]│                       │                    │
     │     [Product Card 2]│                       │                    │
     │     [Product Card 3]│                       │                    │
     │◀────────────────────┤                       │                    │
     │                     │                       │                    │
     │ 21. User clicks     │                       │                    │
     │     "Add to Cart"   │                       │                    │
     │     on Product 1    │                       │                    │
     │────────────────────▶                       │                    │
     │                     │                       │                    │
     │ 22. Redirect to     │                       │                    │
     │     /cart/12345:1   │                       │                    │
     │────────────────────▶                       │                    │
     │     (Shopify cart)  │                       │                    │
```

### 3.3 Admin Search Flow

```
┌──────────┐        ┌──────────────────┐        ┌─────────────┐
│  Admin   │        │ ProductSearch    │        │ Backend API │
│  User    │        │ Assistant Page   │        │             │
└────┬─────┘        └──────┬───────────┘        └──────┬──────┘
     │                     │                           │
     │ 1. Open page        │                           │
     │────────────────────▶                           │
     │                     │                           │
     │ 2. Show toggle:     │                           │
     │    [🔍 Quick] [🤖 AI]                           │
     │◀────────────────────┤                           │
     │                     │                           │
     │ 3. Default: Quick   │                           │
     │    mode active      │                           │
     │                     │                           │
     │ 4. Type "gundam"    │                           │
     │────────────────────▶                           │
     │                     │                           │
     │                5. Instant search (existing)     │
     │                   GET /api/products/search      │
     │                     ├──────────────────────────▶│
     │                     │◀──────────────────────────┤
     │                     │   [{products}]            │
     │                     │                           │
     │ 6. Display grid:    │                           │
     │    [Card] [Card]... │                           │
     │◀────────────────────┤                           │
     │                     │                           │
     │ 7. Click AI toggle  │                           │
     │────────────────────▶                           │
     │                     │                           │
     │ 8. Switch to AI mode│                           │
     │    Show chat UI     │                           │
     │◀────────────────────┤                           │
     │                     │                           │
     │ 9. Ask: "Show me    │                           │
     │    beginner kits"   │                           │
     │────────────────────▶                           │
     │                     │                           │
     │                10. POST /api/chat/message       │
     │                   (same as customer flow)       │
     │                     ├──────────────────────────▶│
     │                     │◀──────────────────────────┤
     │                     │   {AI response + products}│
     │                     │                           │
     │ 11. Display:        │                           │
     │     Chat bubble +   │                           │
     │     Product cards   │                           │
     │◀────────────────────┤                           │
```

---

## 4. Security Architecture

### 4.1 Security Layers

```
┌────────────────────────────────────────────────┐
│  Layer 1: Network Security                    │
├────────────────────────────────────────────────┤
│  - HTTPS only (TLS 1.3)                        │
│  - No HTTP endpoints                           │
│  - Valid SSL certificates                      │
│  - HSTS headers                                │
└────────────────────────────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────────────┐
│  Layer 2: Authentication & Authorization       │
├────────────────────────────────────────────────┤
│  Shopify OAuth 2.0:                            │
│  - Client credentials (API key/secret)         │
│  - HMAC signature verification                 │
│  - Nonce/state (CSRF protection)               │
│  - Access token per shop                       │
│                                                │
│  API Authentication:                           │
│  - Shop domain validation                      │
│  - Access token verification                   │
│  - No public endpoints                         │
└────────────────────────────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────────────┐
│  Layer 3: Request Validation                   │
├────────────────────────────────────────────────┤
│  - Input sanitization                          │
│  - XSS protection                              │
│  - SQL injection prevention (JPA)              │
│  - JSON schema validation                      │
│  - Message length limits (2000 chars)          │
│  - Shop domain format validation               │
└────────────────────────────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────────────┐
│  Layer 4: CORS & Origin Control                │
├────────────────────────────────────────────────┤
│  Allowed Origins:                              │
│  - https://hearnshobbies.com                   │
│  - https://www.hearnshobbies.com               │
│  - http://localhost:3000 (dev only)            │
│                                                │
│  Configuration:                                │
│  - Credentials: true                           │
│  - Methods: GET, POST only                     │
│  - No wildcard origins                         │
└────────────────────────────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────────────┐
│  Layer 5: Rate Limiting                        │
├────────────────────────────────────────────────┤
│  IP-based:                                     │
│  - 100 requests/minute per IP                  │
│                                                │
│  Shop-based:                                   │
│  - 500 requests/hour per shop                  │
│  - 10 concurrent conversations per shop        │
│                                                │
│  External APIs:                                │
│  - Respect Claude rate limits                  │
│  - Respect Shopify rate limits                 │
└────────────────────────────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────────────┐
│  Layer 6: Data Protection                      │
├────────────────────────────────────────────────┤
│  At Rest:                                      │
│  - Access tokens encrypted in DB               │
│  - Database credentials in env vars            │
│  - No secrets in code                          │
│                                                │
│  In Transit:                                   │
│  - HTTPS for all API calls                     │
│  - Secure WebSocket (wss://) if needed         │
│                                                │
│  In Memory:                                    │
│  - Credentials not logged                      │
│  - Sensitive data cleared after use            │
└────────────────────────────────────────────────┘
```

### 4.2 OAuth Security Flow

**HMAC Verification Process:**

```java
// Step 1: Extract parameters from request
String hmac = request.getParameter("hmac");
String queryString = request.getQueryString();

// Step 2: Remove hmac from query string
String message = queryString.replaceAll("&?hmac=[^&]*", "");

// Step 3: Sort parameters alphabetically
String[] params = message.split("&");
Arrays.sort(params);
message = String.join("&", params);

// Step 4: Compute HMAC-SHA256
Mac mac = Mac.getInstance("HmacSHA256");
SecretKeySpec key = new SecretKeySpec(
    apiSecret.getBytes(UTF_8),
    "HmacSHA256"
);
mac.init(key);
byte[] digest = mac.doFinal(message.getBytes(UTF_8));
String computed = Hex.encodeHexString(digest);

// Step 5: Compare (constant-time comparison)
return MessageDigest.isEqual(
    computed.getBytes(),
    hmac.getBytes()
);
```

---

## 5. Deployment Architecture

### 5.1 Production Deployment

```
┌──────────────────────────────────────────────┐
│         Internet / Public Access             │
└───────────────────┬──────────────────────────┘
                    │
        ┌───────────┴────────────┐
        │                        │
        ▼                        ▼
┌───────────────┐    ┌─────────────────────┐
│   Shopify     │    │   Your Domain       │
│   CDN         │    │   (Railway)         │
│               │    │                     │
│ hearnshobbies │    │ your-app.railway.app│
│   .com        │    └──────────┬──────────┘
└───────┬───────┘               │
        │                       │
        │ ┌─────────────────────┘
        │ │
        ▼ ▼
┌──────────────────────────────────────────────┐
│         Railway Platform                     │
├──────────────────────────────────────────────┤
│                                              │
│  ┌────────────────────────────────────────┐ │
│  │  Spring Boot App (Docker Container)    │ │
│  │  - Auto-scaling enabled                │ │
│  │  - Health checks                       │ │
│  │  - Logging to stdout                   │ │
│  └────────────────────────────────────────┘ │
│                    │                         │
│                    ▼                         │
│  ┌────────────────────────────────────────┐ │
│  │  PostgreSQL Database (Railway)         │ │
│  │  - Automatic backups                   │ │
│  │  - Connection pooling                  │ │
│  └────────────────────────────────────────┘ │
│                                              │
│  Environment Variables:                      │
│  - DATABASE_URL (auto-injected)              │
│  - SHOPIFY_APP_API_KEY                       │
│  - SHOPIFY_APP_API_SECRET                    │
│  - ANTHROPIC_API_KEY                         │
│                                              │
└──────────────────────────────────────────────┘
                    │
                    │ External API Calls
                    │
        ┌───────────┴────────────┐
        │                        │
        ▼                        ▼
┌───────────────┐    ┌─────────────────────┐
│   Shopify     │    │   Anthropic         │
│   Admin API   │    │   Claude API        │
│               │    │                     │
│ GraphQL       │    │ Messages API        │
│ Rate limited  │    │ Rate limited        │
└───────────────┘    └─────────────────────┘
```

### 5.2 Development Environment

```
┌──────────────────────────────────────────────┐
│         Local Development                    │
├──────────────────────────────────────────────┤
│                                              │
│  Terminal 1: Backend                         │
│  $ mvn spring-boot:run                       │
│  → http://localhost:8080                     │
│                                              │
│  Terminal 2: Frontend                        │
│  $ npm run dev                               │
│  → http://localhost:3000                     │
│  → Proxies /api to :8080                     │
│                                              │
│  Terminal 3: Database                        │
│  $ docker run -p 5432:5432 postgres          │
│  → localhost:5432                            │
│                                              │
│  Terminal 4: Shopify CLI (theme extension)   │
│  $ shopify app dev                           │
│  → Tunnel to localhost                       │
│  → ngrok/cloudflare tunnel                   │
│                                              │
└──────────────────────────────────────────────┘
```

---

## 6. Component Dependencies

### 6.1 Dependency Graph

```
┌─────────────────────────────────────────┐
│  Frontend Dependencies                  │
├─────────────────────────────────────────┤
│                                         │
│  ProductSearchAssistant.jsx             │
│         │                               │
│         ├──▶ SearchModeToggle.jsx       │
│         │                               │
│         ├──▶ SearchBar.jsx (existing)   │
│         │                               │
│         ├──▶ ChatInterface.jsx          │
│         │         │                     │
│         │         ├──▶ ChatMessageBubble│
│         │         │         │           │
│         │         │         ├──▶ ResponseParser.js
│         │         │         │           │
│         │         │         └──▶ ChatProductCard.jsx
│         │         │                     │
│         │         └──▶ ChatInput.jsx    │
│         │                               │
│         └──▶ ProductCard.jsx (existing) │
│                                         │
│  api.js (HTTP client)                   │
│         │                               │
│         └──▶ axios                      │
│                                         │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│  Backend Dependencies                   │
├─────────────────────────────────────────┤
│                                         │
│  ShopifyInstallController               │
│         │                               │
│         ├──▶ ShopifyOAuthService        │
│         │         │                     │
│         │         └──▶ ShopifyAppConfig │
│         │                               │
│         └──▶ ShopifyShopService         │
│                    │                    │
│                    └──▶ ShopifyShopRepo │
│                                         │
│  ShopifyChatController                  │
│         │                               │
│         ├──▶ ChatAgentService           │
│         │         │                     │
│         │         ├──▶ ProductService   │
│         │         │         │           │
│         │         │         └──▶ ShopifyGraphQLClient
│         │         │                     │
│         │         ├──▶ ChatbotConfigSvc │
│         │         │                     │
│         │         └──▶ WebClient → Claude API
│         │                               │
│         └──▶ ShopifyShopService         │
│                                         │
└─────────────────────────────────────────┘
```

### 6.2 External Dependencies

**Maven (Backend):**
```xml
<!-- Spring Boot -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Database -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>

<!-- JSON -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>

<!-- Utilities -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
</dependency>
```

**npm (Frontend):**
```json
{
  "dependencies": {
    "react": "^18.2.0",
    "react-dom": "^18.2.0",
    "react-router-dom": "^6.20.0",
    "axios": "^1.6.2"
  },
  "devDependencies": {
    "@vitejs/plugin-react": "^4.2.1",
    "vite": "^5.0.8",
    "tailwindcss": "^3.3.6",
    "autoprefixer": "^10.4.16",
    "postcss": "^8.4.32"
  }
}
```

---

## 7. Performance Considerations

### 7.1 Response Time Targets

| Operation | Target | Maximum |
|-----------|--------|---------|
| Quick Search | < 100ms | 300ms |
| AI Chat Response | < 3s | 5s |
| Modal Open | < 100ms | 200ms |
| Product Card Render | < 50ms | 100ms |
| OAuth Callback | < 500ms | 1s |
| Page Load | < 1s | 2s |

### 7.2 Scalability

**Concurrent Users:**
- Design for 100 concurrent chat sessions
- Each session = 1 WebFlux thread
- Reactive/non-blocking IO

**Database Connections:**
- Connection pool: 10 connections
- Max wait: 30 seconds
- Idle timeout: 10 minutes

**Claude API:**
- Rate limit: 50 requests/minute (tier dependent)
- Timeout: 30 seconds per request
- Retry: Exponential backoff (3 retries)

**Shopify API:**
- Rate limit: 1000 points/second
- Query cost varies (1-1000 points)
- Bucket strategy for rate limiting

### 7.3 Caching Strategy

```
┌────────────────────────────────────┐
│  Cache Layers                      │
├────────────────────────────────────┤
│                                    │
│  L1: Shop Configuration (In-Memory)│
│  - TTL: 5 minutes                  │
│  - Invalidate on config update     │
│  - Reduces DB queries              │
│                                    │
│  L2: Product Search Results        │
│  - Redis (optional)                │
│  - TTL: 1 minute                   │
│  - Key: shop + query hash          │
│  - Reduces Shopify API calls       │
│                                    │
│  L3: Static Assets (CDN)           │
│  - JavaScript, CSS                 │
│  - Cache-Control: max-age=86400    │
│  - Versioned filenames             │
│                                    │
└────────────────────────────────────┘
```

---

## 8. Error Handling & Resilience

### 8.1 Error Handling Strategy

```
┌────────────────────────────────────┐
│  Error Handling Layers             │
├────────────────────────────────────┤
│                                    │
│  Layer 1: Client-Side (JavaScript) │
│  - Try/catch for API calls         │
│  - User-friendly error messages    │
│  - Fallback to traditional search  │
│  - Retry failed requests (3x)      │
│                                    │
│  Layer 2: API Layer (Controllers)  │
│  - @ExceptionHandler methods       │
│  - Standard error response format  │
│  - HTTP status codes               │
│  - Detailed error logging          │
│                                    │
│  Layer 3: Service Layer            │
│  - Reactive error handling (onError)│
│  - Circuit breaker (optional)      │
│  - Fallback strategies             │
│  - Timeout handling                │
│                                    │
│  Layer 4: External APIs            │
│  - Retry with backoff              │
│  - Timeout after 30s               │
│  - Fallback responses              │
│  - Error classification            │
│                                    │
└────────────────────────────────────┘
```

### 8.2 Resilience Patterns

**Circuit Breaker (Optional):**
```java
@Service
public class ChatAgentService {

    @CircuitBreaker(name = "claude-api",
                    fallbackMethod = "fallbackResponse")
    public Mono<ChatMessage> processChat(ChatRequest request) {
        // Call Claude API
    }

    private Mono<ChatMessage> fallbackResponse(
        ChatRequest request,
        Exception e
    ) {
        return Mono.just(new ChatMessage(
            "assistant",
            "I'm having trouble connecting right now. " +
            "Please try again or use Quick Search."
        ));
    }
}
```

**Retry Strategy:**
```java
return webClient.post()
    .uri("/messages")
    .bodyValue(request)
    .retrieve()
    .bodyToMono(JsonNode.class)
    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
        .filter(throwable -> throwable instanceof WebClientException)
        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
            new RuntimeException("Max retries exceeded")
        )
    );
```

---

## Next Steps

**Continue to:** [02-DATABASE-SCHEMA.md](./02-DATABASE-SCHEMA.md) - Database design and migrations

**Or return to:** [00-OVERVIEW.md](./00-OVERVIEW.md) - Documentation index

---

*Documentation Version: 1.0*
*Last Updated: 2025-10-30*
*Project: Hearn's Hobbies AI Search Enhancement*
