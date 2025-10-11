# AI Chat Agent Status - v0.2.0-chatbot-basic

**Last Updated:** 2025-10-11
**Version:** v0.2.0-chatbot-basic (WORKING4 checkpoint)
**Status:** Basic chatbot working, pending full configuration system

## Overview

This document tracks the AI Chat Agent implementation, which adds conversational product search and customer support capabilities using Anthropic's Claude API.

## What's Working ✅

### 1. Claude API Integration
- **Service:** `ChatAgentService.java` processes chat requests
- **Model:** claude-3-5-sonnet-20241022
- **Endpoint:** `POST /api/chat/message`
- **Features:**
  - Sends messages to Claude
  - Includes conversation history
  - Returns AI-generated responses
  - Proper error handling and logging

### 2. CORS Configuration Fixed
- **Issue:** 403 Forbidden errors in production (Railway)
- **Root Cause:** CORS only allowed localhost, not Railway domains
- **Fix:** Pattern-based origin matching in `CorsConfig.java`:
  ```java
  .allowedOriginPatterns("http://localhost:*", "https://*.railway.app")
  ```
- **Result:** Chatbot works on both localhost and Railway production

### 3. AI Configuration API
Three new endpoints in `ConfigController.java`:

**GET /api/config/ai**
```json
{
  "model": "claude-3-5-sonnet-20241022",
  "maxTokens": 1024,
  "temperature": 0.7
}
```

**PUT /api/config/ai**
```json
{
  "model": "claude-3-5-haiku-20241022",
  "maxTokens": 2048,
  "temperature": 0.5
}
```

**GET /api/config/models**
```json
{
  "models": [
    "claude-3-5-sonnet-20241022",
    "claude-3-5-haiku-20241022",
    "claude-3-opus-20240229"
  ],
  "current": "claude-3-5-sonnet-20241022"
}
```

**GET /api/config/prompt**
```json
{
  "prompt": "You are a helpful sales and customer support assistant...",
  "editable": false,
  "message": "System prompt is loaded from file."
}
```

### 4. Settings UI
React Settings page (`frontend/src/pages/Settings.jsx`) includes:
- **AI Configuration tab:**
  - Model selection dropdown
  - Temperature slider (0.0-1.0)
  - Max tokens input (256-4096)
  - Real-time updates (runtime only, not persisted)
- **System Prompt tab:**
  - View current prompt template
  - Read-only for now

### 5. External System Prompt
- **File:** `src/main/resources/prompts/system-prompt.txt`
- **Loading:** `@PostConstruct` in `ChatAgentService.java`
- **Purpose:** Separates prompt from code for easier editing

## Known Issues ⚠️

### 1. Tool Use Not Implemented
**Problem:** AI simulates function calls instead of executing them

**Example:**
```
User: "Find me white acrylic paint"
AI: "Let me search for the best acrylic white paint options.
{search_products("tamiya acrylic white")}
I found several options..."
```

**Why:**
- System prompt mentions `search_products` function
- But Claude API request doesn't include `tools` parameter
- Claude assumes it should simulate the call

**Impact:** AI can't actually search products, makes up responses

**Fix Needed:** Implement Claude tool use (function calling)

### 2. Hardcoded System Prompt
**Problem:** Prompt is hardcoded in `system-prompt.txt`

**Issues:**
- Gundam-specific (should be generic hobby store)
- No placeholders for store name, description, categories
- Can't be edited through Settings UI
- Not configurable per deployment

**Example Hardcoding:**
```
"You are a helpful sales and customer support assistant for an online Gundam model kit store."
```

**Fix Needed:** Dynamic prompt generation from configuration

### 3. No Chatbot Configuration UI
**Problem:** No way to configure chatbot behavior through UI

**Missing Features:**
- Store identity (name, description)
- What products we sell
- Conversation scope rules
- Response style preferences
- Tool configuration

**Fix Needed:** Complete configuration system (see Next Steps)

## Technical Implementation

### Architecture

```
React Frontend (Chat UI)
    ↓ POST /api/chat/message
ChatController.java
    ↓
ChatAgentService.java
    ↓ processChat()
    ├─ Build system prompt (from file)
    ├─ Add conversation history
    ├─ Call Claude API (WebClient)
    └─ Return ChatMessage
    ↓
Claude API Response
    ↓
Frontend displays message
```

### Key Files

**Backend:**
- `ChatController.java` - REST endpoints for chat
- `ChatAgentService.java` - Claude API integration
- `ConfigController.java` - Configuration management
- `ChatRequest.java` - Request model
- `ChatMessage.java` - Message model
- `ApiResponse.java` - Response wrapper
- `CorsConfig.java` - CORS configuration
- `application.yml` - AI configuration
- `prompts/system-prompt.txt` - System prompt template

**Frontend:**
- `Chat.jsx` - Chat interface page
- `Settings.jsx` - Configuration UI
- `api.js` - API service layer

### Environment Variables

**Required:**
```bash
ANTHROPIC_API_KEY=sk-ant-api03-...
SHOPIFY_SHOP_URL=hearnshobbies.myshopify.com
SHOPIFY_ACCESS_TOKEN=shpat_...
SHOPIFY_API_VERSION=2025-01
```

**Optional (with defaults):**
```bash
ANTHROPIC_MODEL=claude-3-5-sonnet-20241022
ANTHROPIC_MAX_TOKENS=1024
ANTHROPIC_TEMPERATURE=0.7
ANTHROPIC_SYSTEM_PROMPT_FILE=classpath:prompts/system-prompt.txt
```

### Configuration in application.yml

```yaml
anthropic:
  api:
    key: ${ANTHROPIC_API_KEY:}
    version: ${ANTHROPIC_API_VERSION:2023-06-01}
  model: ${ANTHROPIC_MODEL:claude-3-5-sonnet-20241022}
  max-tokens: ${ANTHROPIC_MAX_TOKENS:1024}
  temperature: ${ANTHROPIC_TEMPERATURE:0.7}
  system-prompt-file: ${ANTHROPIC_SYSTEM_PROMPT_FILE:classpath:prompts/system-prompt.txt}
```

## Testing

### Local Testing
```bash
# Start server
SHOPIFY_SHOP_URL=hearnshobbies.myshopify.com \
SHOPIFY_ACCESS_TOKEN=shpat_... \
ANTHROPIC_API_KEY=sk-ant-api03-... \
mvn spring-boot:run

# Test chat endpoint
curl -X POST http://localhost:8080/api/chat/message \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Tell me about Gundam models",
    "conversationHistory": []
  }'
```

### Production (Railway)
```bash
# Test production endpoint
curl -X POST https://shopify-data-api-production.up.railway.app/api/chat/message \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Tell me about Gundam models",
    "conversationHistory": []
  }'
```

## Next Steps 📋

### Phase 1: Backend Configuration System
1. **Create ChatbotConfig.java** model
   - Store identity fields
   - Behavior rules
   - Tool configuration
   - Response style preferences

2. **Update application.yml** with chatbot defaults
   - Use environment variables
   - Provide sensible defaults
   - Support generic hobby store

3. **Add chatbot config endpoints**
   - `GET /api/config/chatbot` - Get all settings
   - `PUT /api/config/chatbot` - Update settings
   - `POST /api/config/chatbot/reset` - Reset to defaults
   - `GET /api/config/chatbot/preview-prompt` - Preview generated prompt

4. **Implement dynamic prompt generation**
   - `buildSystemPrompt()` in ChatAgentService
   - Use configuration instead of file
   - Support placeholders: {STORE_NAME}, {STORE_DESCRIPTION}, etc.

### Phase 2: Tool Use Implementation
5. **Add tools parameter to Claude API request**
   ```json
   {
     "model": "claude-3-5-sonnet-20241022",
     "messages": [...],
     "tools": [{
       "name": "search_products",
       "description": "Search the product catalog",
       "input_schema": {
         "type": "object",
         "properties": {
           "query": {"type": "string"}
         }
       }
     }]
   }
   ```

6. **Handle tool_use responses**
   - Detect `stop_reason: "tool_use"`
   - Extract tool name and parameters
   - Execute `productService.searchProducts()`
   - Send results back to Claude

7. **Implement multi-turn conversation**
   - Request 1: User message → Claude requests tool
   - Request 2: Tool results → Claude generates response
   - Return final response to user

### Phase 3: Frontend Configuration UI
8. **Add "Chatbot" tab to Settings.jsx**
   - Create `ChatbotSettings.jsx` component
   - 5 configuration sections:
     1. Store Identity
     2. Conversation Scope
     3. Product Search Tool
     4. Response Style
     5. Advanced Options

9. **Implement form validation**
   - Store name required
   - Max results 1-20
   - Proper error messages

10. **Add preview functionality**
    - "Preview Prompt" button
    - Modal showing generated system prompt
    - Update preview when settings change

### Phase 4: Testing and Deployment
11. **End-to-end testing**
    - Test tool use flow
    - Test all configuration options
    - Verify Railway deployment

12. **Documentation updates**
    - Update CURRENT_STATUS.md
    - Create configuration guide
    - Document tool use implementation

## Benefits of Next Phase

When configuration system is complete:

✅ **Zero Hardcoding** - Everything UI-configurable
✅ **Generic** - Works for any hobby store
✅ **Flexible** - Easy to adapt to different use cases
✅ **Previewable** - See prompts before applying
✅ **Resetable** - Back to defaults anytime
✅ **Functional** - AI can actually search products

## Development Timeline

- **v0.1.0** - Basic API, Product/Order endpoints
- **v0.2.0-chatbot-basic** - THIS VERSION (basic chatbot, CORS fix)
- **v0.3.0** - Full configuration system + tool use
- **v0.4.0** - Database persistence, advanced features

## Links

- **Production:** https://shopify-data-api-production.up.railway.app
- **GitHub:** https://github.com/NicP83/shopify-data-api
- **Claude Docs:** https://docs.anthropic.com/claude/docs/tool-use
- **Shopify API:** https://shopify.dev/docs/api/admin-graphql

---

**Current Status:** Basic chatbot working, ready for configuration system implementation
