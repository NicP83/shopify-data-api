# Phase 2 Enhancements: Search & AI Configuration

**Status:** In Planning
**Target Checkpoint:** WORKING5
**Created:** 2025-10-11

---

## Overview

This document outlines the enhancements to be made after WORKING4 (AI Chat Agent basic implementation). These improvements focus on two critical areas:

1. **Enhanced Product Search** - Multi-field searching with archived product filtering
2. **Configurable AI Chatbot** - Settings UI and externalized configuration

---

## Part 1: Enhanced Product Search

### Current Limitations

- **Problem 1:** Search only uses basic Shopify query parameter (passes query string directly)
- **Problem 2:** No filtering for archived products (returns ALL product statuses)
- **Problem 3:** Limited search scope (doesn't explicitly search body text, metafields, or use advanced Shopify query syntax)
- **Problem 4:** No option for users to include/exclude archived products

### Proposed Solution

Implement Shopify's advanced query syntax to search across multiple fields and add status filtering.

### Backend Changes

#### 1.1 Update ProductService.java

**File:** `src/main/java/com/shopify/api/service/ProductService.java`

**Current Implementation (lines 166-215):**
```java
public Map<String, Object> searchProducts(String searchQuery, int first) {
    String query = String.format("""
        {
          products(first: %d, query: "%s") {
            // ... GraphQL query
          }
        }
        """, Math.min(first, 250), searchQuery);
}
```

**New Implementation:**
```java
public Map<String, Object> searchProducts(String searchQuery, int first, boolean includeArchived) {
    // Build advanced Shopify query syntax
    String shopifyQuery = buildAdvancedSearchQuery(searchQuery, includeArchived);

    String query = String.format("""
        {
          products(first: %d, query: "%s") {
            edges {
              node {
                id
                title
                description
                handle
                onlineStoreUrl
                status
                vendor
                productType
                tags
                variants(first: 5) {
                  edges {
                    node {
                      id
                      title
                      sku
                      price
                    }
                  }
                }
                images(first: 1) {
                  edges {
                    node {
                      url
                    }
                  }
                }
              }
            }
          }
        }
        """, Math.min(first, 250), shopifyQuery);

    return executeGraphQLQuery(query);
}

private String buildAdvancedSearchQuery(String userQuery, boolean includeArchived) {
    // Escape special characters in user query
    String escapedQuery = userQuery.replace("\"", "\\\"");

    // Build multi-field search query
    StringBuilder queryBuilder = new StringBuilder();
    queryBuilder.append("(");
    queryBuilder.append("title:*").append(escapedQuery).append("*");
    queryBuilder.append(" OR body:*").append(escapedQuery).append("*");
    queryBuilder.append(" OR tag:").append(escapedQuery);
    queryBuilder.append(" OR vendor:*").append(escapedQuery).append("*");
    queryBuilder.append(")");

    // Add status filter if not including archived
    if (!includeArchived) {
        queryBuilder.append(" AND status:active");
    }

    return queryBuilder.toString();
}
```

**Changes:**
- Add `boolean includeArchived` parameter (default: false)
- Create `buildAdvancedSearchQuery()` helper method
- Search across: title, body, tags, vendor
- Apply `status:active` filter when `includeArchived=false`
- Escape special characters for Shopify query syntax

#### 1.2 Update ProductController.java

**File:** `src/main/java/com/shopify/api/controller/ProductController.java`

**Current Implementation:**
```java
@GetMapping("/search")
public ResponseEntity<ApiResponse<Map<String, Object>>> searchProducts(
        @RequestParam String q,
        @RequestParam(defaultValue = "10") int first
) {
    // ...
}
```

**New Implementation:**
```java
@GetMapping("/search")
public ResponseEntity<ApiResponse<Map<String, Object>>> searchProducts(
        @RequestParam String q,
        @RequestParam(defaultValue = "10") int first,
        @RequestParam(defaultValue = "false") boolean includeArchived
) {
    logger.info("Searching products: query='{}', first={}, includeArchived={}", q, first, includeArchived);

    try {
        Map<String, Object> products = productService.searchProducts(q, first, includeArchived);
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", products));
    } catch (Exception e) {
        logger.error("Error searching products: {}", e.getMessage());
        return ResponseEntity.ok(ApiResponse.error("Failed to search products: " + e.getMessage()));
    }
}
```

**Changes:**
- Add `@RequestParam(defaultValue = "false") boolean includeArchived`
- Pass to `productService.searchProducts(q, first, includeArchived)`
- Update logging

### Frontend Changes

#### 1.3 Update api.js

**File:** `frontend/src/services/api.js`

**Current Implementation:**
```javascript
searchProducts: (query, limit = 10) => {
  return apiClient.get('/products/search', {
    params: { q: query, first: limit }
  })
}
```

**New Implementation:**
```javascript
searchProducts: (query, limit = 10, includeArchived = false) => {
  return apiClient.get('/products/search', {
    params: {
      q: query,
      first: limit,
      includeArchived: includeArchived
    }
  })
}
```

#### 1.4 Update ProductSearch.jsx

**File:** `frontend/src/pages/ProductSearch.jsx`

**Add state for archived filter:**
```javascript
const [includeArchived, setIncludeArchived] = useState(false)
```

**Update search function:**
```javascript
const handleSearch = async () => {
  if (!searchQuery.trim()) {
    setProducts([])
    return
  }

  setLoading(true)
  setError(null)

  try {
    const response = await api.searchProducts(searchQuery, 20, includeArchived)

    if (response.data.success) {
      const edges = response.data.data.edges || []
      setProducts(edges.map(edge => edge.node))
    } else {
      setError(response.data.message)
    }
  } catch (err) {
    console.error('Error searching products:', err)
    setError('Failed to search products. Please try again.')
  } finally {
    setLoading(false)
  }
}
```

**Add checkbox UI (after search bar):**
```jsx
<div className="mb-4">
  <label className="flex items-center space-x-2 text-sm text-gray-700">
    <input
      type="checkbox"
      checked={includeArchived}
      onChange={(e) => setIncludeArchived(e.target.checked)}
      className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
    />
    <span>Include archived products</span>
  </label>
</div>
```

**Update status badge display:**
```jsx
{includeArchived && product.status !== 'ACTIVE' && (
  <span className="inline-block px-2 py-1 text-xs font-semibold text-red-800 bg-red-200 rounded-full">
    {product.status}
  </span>
)}
```

### Testing Checklist

- [ ] Search with single word returns results from title, body, tags, vendor
- [ ] Archived products excluded by default
- [ ] Checkbox shows archived products when enabled
- [ ] Status badges appear for archived products
- [ ] Empty search query handled gracefully
- [ ] Special characters in query escaped properly
- [ ] Search response time < 2 seconds

---

## Part 2: Configurable AI Chatbot

### Current State

**File:** `src/main/java/com/shopify/api/service/ChatAgentService.java`

**Current Configuration (lines 30-40):**
```java
@Value("${anthropic.api.key:}")
private String anthropicApiKey;

@Value("${anthropic.api.version:2023-06-01}")
private String anthropicApiVersion;

@Value("${anthropic.model:claude-3-5-sonnet-20241022}")
private String anthropicModel;

// Hardcoded in processChat() method:
private static final int MAX_TOKENS = 1024;
// No temperature configuration
```

**System Prompt Location (lines 95-117):**
```java
private String buildSystemPrompt() {
    return """
            You are a helpful sales and customer support assistant...
            """.formatted(shopUrl, shopUrl);
}
```

### Issues

1. System prompt hardcoded in Java (requires recompile to change)
2. `max_tokens` hardcoded to 1024
3. No `temperature` configuration
4. No user-facing settings UI
5. Cannot tune AI behavior without code changes

### Proposed Solution

Externalize configuration and create settings management UI.

### Backend Changes

#### 2.1 Update application.properties

**File:** `src/main/resources/application.properties`

**Add new configuration:**
```properties
# AI Chat Configuration
anthropic.api.key=${ANTHROPIC_API_KEY:}
anthropic.api.version=2023-06-01
anthropic.model=claude-3-5-sonnet-20241022
anthropic.max.tokens=1024
anthropic.temperature=0.7
anthropic.system.prompt.file=classpath:prompts/system-prompt.txt
```

#### 2.2 Create External Prompt File

**File:** `src/main/resources/prompts/system-prompt.txt`

**Content:**
```text
You are a helpful sales and customer support assistant for an online Gundam model kit store.
Your role is to help customers find products, answer questions, and assist with purchases.

You have access to the store's product catalog and can search for products by name or description.
When customers ask about products, you should:
1. Search for relevant products
2. Describe the products in detail
3. Provide pricing information
4. Generate direct "Add to Cart" links that customers can click

Store URL: {SHOP_URL}

When mentioning products, always provide:
- Product name and SKU
- Price
- A direct cart link in this format: https://{SHOP_URL}/cart/VARIANT_ID:1

Be friendly, enthusiastic about Gundam models, and help customers make informed decisions.
If you don't have information about a specific product, be honest and suggest searching the catalog.

You can search products by calling the search_products function with relevant keywords.
```

**Note:** Use `{SHOP_URL}` as placeholder for runtime replacement.

#### 2.3 Update ChatAgentService.java

**File:** `src/main/java/com/shopify/api/service/ChatAgentService.java`

**Add new configuration fields:**
```java
@Value("${anthropic.max.tokens:1024}")
private int maxTokens;

@Value("${anthropic.temperature:0.7}")
private double temperature;

@Value("${anthropic.system.prompt.file:classpath:prompts/system-prompt.txt}")
private String systemPromptFile;

@Autowired
private ResourceLoader resourceLoader;

private String systemPromptTemplate;
```

**Update buildSystemPrompt():**
```java
@PostConstruct
public void loadSystemPrompt() {
    try {
        Resource resource = resourceLoader.getResource(systemPromptFile);
        systemPromptTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        logger.info("Loaded system prompt from: {}", systemPromptFile);
    } catch (IOException e) {
        logger.error("Failed to load system prompt file: {}", e.getMessage());
        // Fall back to default prompt
        systemPromptTemplate = """
            You are a helpful sales and customer support assistant for an online Gundam model kit store.
            Store URL: {SHOP_URL}
            """;
    }
}

private String buildSystemPrompt() {
    return systemPromptTemplate.replace("{SHOP_URL}", shopUrl);
}
```

**Update processChat() to use configurable values:**
```java
public Mono<ChatMessage> processChat(ChatRequest chatRequest) {
    // ... existing code ...

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("model", anthropicModel);
    requestBody.put("max_tokens", maxTokens);  // Use configured value
    requestBody.put("temperature", temperature);  // Add temperature
    requestBody.put("system", buildSystemPrompt());
    requestBody.put("messages", messages);

    // ... rest of method
}
```

#### 2.4 Create ConfigController.java

**File:** `src/main/java/com/shopify/api/controller/ConfigController.java`

**New controller for settings management:**
```java
package com.shopify.api.controller;

import com.shopify.api.model.ApiResponse;
import com.shopify.api.service.ChatAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:8080"})
public class ConfigController {

    private static final Logger logger = LoggerFactory.getLogger(ConfigController.class);

    @Value("${anthropic.model}")
    private String anthropicModel;

    @Value("${anthropic.max.tokens}")
    private int maxTokens;

    @Value("${anthropic.temperature}")
    private double temperature;

    @Autowired
    private ChatAgentService chatAgentService;

    /**
     * GET /api/config/ai - Retrieve current AI configuration
     */
    @GetMapping("/ai")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAIConfig() {
        logger.info("Fetching AI configuration");

        Map<String, Object> config = new HashMap<>();
        config.put("model", anthropicModel);
        config.put("maxTokens", maxTokens);
        config.put("temperature", temperature);
        config.put("availableModels", new String[]{
            "claude-3-5-sonnet-20241022",
            "claude-3-opus-20240229",
            "claude-3-sonnet-20240229",
            "claude-3-haiku-20240307"
        });

        return ResponseEntity.ok(ApiResponse.success("AI configuration retrieved", config));
    }

    /**
     * GET /api/config/prompt - View current system prompt
     */
    @GetMapping("/prompt")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPrompt() {
        logger.info("Fetching system prompt");

        Map<String, String> promptData = new HashMap<>();
        promptData.put("prompt", chatAgentService.getSystemPromptTemplate());

        return ResponseEntity.ok(ApiResponse.success("System prompt retrieved", promptData));
    }

    /**
     * PUT /api/config/ai - Update AI configuration (in-memory)
     * Note: Changes are temporary and reset on restart
     */
    @PutMapping("/ai")
    public ResponseEntity<ApiResponse<String>> updateAIConfig(@RequestBody Map<String, Object> config) {
        logger.info("Updating AI configuration: {}", config);

        try {
            if (config.containsKey("maxTokens")) {
                chatAgentService.setMaxTokens((Integer) config.get("maxTokens"));
            }
            if (config.containsKey("temperature")) {
                chatAgentService.setTemperature(((Number) config.get("temperature")).doubleValue());
            }
            if (config.containsKey("model")) {
                chatAgentService.setModel((String) config.get("model"));
            }

            return ResponseEntity.ok(ApiResponse.success("Configuration updated successfully", "OK"));
        } catch (Exception e) {
            logger.error("Error updating AI configuration: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error("Failed to update configuration: " + e.getMessage()));
        }
    }
}
```

**Add getters/setters to ChatAgentService.java:**
```java
public String getSystemPromptTemplate() {
    return systemPromptTemplate;
}

public void setMaxTokens(int maxTokens) {
    this.maxTokens = maxTokens;
    logger.info("Max tokens updated to: {}", maxTokens);
}

public void setTemperature(double temperature) {
    this.temperature = temperature;
    logger.info("Temperature updated to: {}", temperature);
}

public void setModel(String model) {
    this.anthropicModel = model;
    logger.info("Model updated to: {}", model);
}
```

### Frontend Changes

#### 2.5 Create SettingsPage.jsx

**File:** `frontend/src/pages/SettingsPage.jsx`

```jsx
import { useState, useEffect } from 'react'
import api from '../services/api'

function SettingsPage() {
  const [aiConfig, setAiConfig] = useState(null)
  const [systemPrompt, setSystemPrompt] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState(null)

  useEffect(() => {
    loadSettings()
  }, [])

  const loadSettings = async () => {
    setLoading(true)
    try {
      const [configRes, promptRes] = await Promise.all([
        api.getAIConfig(),
        api.getSystemPrompt()
      ])

      if (configRes.data.success) {
        setAiConfig(configRes.data.data)
      }

      if (promptRes.data.success) {
        setSystemPrompt(promptRes.data.data.prompt)
      }
    } catch (err) {
      console.error('Error loading settings:', err)
      setMessage({ type: 'error', text: 'Failed to load settings' })
    } finally {
      setLoading(false)
    }
  }

  const handleSave = async () => {
    setSaving(true)
    setMessage(null)

    try {
      const response = await api.updateAIConfig(aiConfig)

      if (response.data.success) {
        setMessage({ type: 'success', text: 'Settings saved successfully!' })
      } else {
        setMessage({ type: 'error', text: response.data.message })
      }
    } catch (err) {
      console.error('Error saving settings:', err)
      setMessage({ type: 'error', text: 'Failed to save settings' })
    } finally {
      setSaving(false)
    }
  }

  const handleReset = () => {
    loadSettings()
    setMessage({ type: 'info', text: 'Settings reset to current values' })
  }

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-gray-500">Loading settings...</div>
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto">
      <h1 className="text-3xl font-bold mb-6">Settings</h1>

      {message && (
        <div className={`mb-6 p-4 rounded ${
          message.type === 'success' ? 'bg-green-100 text-green-800' :
          message.type === 'error' ? 'bg-red-100 text-red-800' :
          'bg-blue-100 text-blue-800'
        }`}>
          {message.text}
        </div>
      )}

      {/* AI Configuration Section */}
      <div className="bg-white shadow rounded-lg p-6 mb-6">
        <h2 className="text-xl font-semibold mb-4">AI Configuration</h2>

        <div className="space-y-4">
          {/* Model Selection */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Model
            </label>
            <select
              value={aiConfig?.model || ''}
              onChange={(e) => setAiConfig({...aiConfig, model: e.target.value})}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {aiConfig?.availableModels?.map(model => (
                <option key={model} value={model}>{model}</option>
              ))}
            </select>
          </div>

          {/* Temperature Slider */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Temperature: {aiConfig?.temperature?.toFixed(2)}
            </label>
            <input
              type="range"
              min="0"
              max="1"
              step="0.05"
              value={aiConfig?.temperature || 0.7}
              onChange={(e) => setAiConfig({...aiConfig, temperature: parseFloat(e.target.value)})}
              className="w-full"
            />
            <div className="flex justify-between text-xs text-gray-500 mt-1">
              <span>Precise (0.0)</span>
              <span>Balanced (0.5)</span>
              <span>Creative (1.0)</span>
            </div>
          </div>

          {/* Max Tokens */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Max Tokens
            </label>
            <input
              type="number"
              min="256"
              max="4096"
              value={aiConfig?.maxTokens || 1024}
              onChange={(e) => setAiConfig({...aiConfig, maxTokens: parseInt(e.target.value)})}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <p className="text-xs text-gray-500 mt-1">
              Maximum length of AI responses (256-4096)
            </p>
          </div>
        </div>
      </div>

      {/* System Prompt Section */}
      <div className="bg-white shadow rounded-lg p-6 mb-6">
        <h2 className="text-xl font-semibold mb-4">System Prompt</h2>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            Current System Prompt (Read-Only)
          </label>
          <textarea
            value={systemPrompt}
            readOnly
            rows="10"
            className="w-full px-3 py-2 border border-gray-300 rounded-md bg-gray-50 font-mono text-sm"
          />
          <p className="text-xs text-gray-500 mt-2">
            To edit the system prompt, modify the file: <code>src/main/resources/prompts/system-prompt.txt</code>
          </p>
        </div>
      </div>

      {/* Action Buttons */}
      <div className="flex justify-end space-x-4">
        <button
          onClick={handleReset}
          disabled={saving}
          className="px-6 py-2 border border-gray-300 rounded-md text-gray-700 hover:bg-gray-50 disabled:opacity-50"
        >
          Reset
        </button>
        <button
          onClick={handleSave}
          disabled={saving}
          className="px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50"
        >
          {saving ? 'Saving...' : 'Save Changes'}
        </button>
      </div>

      {/* Warning Notice */}
      <div className="mt-6 p-4 bg-yellow-50 border border-yellow-200 rounded-md">
        <p className="text-sm text-yellow-800">
          <strong>Note:</strong> Configuration changes are temporary and will reset when the application restarts.
          To make permanent changes, update the <code>application.properties</code> file.
        </p>
      </div>
    </div>
  )
}

export default SettingsPage
```

#### 2.6 Update api.js

**File:** `frontend/src/services/api.js`

**Add new API methods:**
```javascript
const api = {
  // ... existing methods ...

  // Configuration
  getAIConfig: () => apiClient.get('/config/ai'),
  updateAIConfig: (config) => apiClient.put('/config/ai', config),
  getSystemPrompt: () => apiClient.get('/config/prompt'),
}
```

#### 2.7 Update Navigation.jsx

**File:** `frontend/src/components/Navigation.jsx`

**Add Settings link:**
```jsx
<Link to="/settings" className="...">
  Settings
</Link>
```

#### 2.8 Update App.jsx

**File:** `frontend/src/App.jsx`

**Add Settings route:**
```jsx
import SettingsPage from './pages/SettingsPage'

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/products" element={<ProductSearch />} />
        <Route path="/chat" element={<ChatAgent />} />
        <Route path="/settings" element={<SettingsPage />} />
        {/* ... other routes ... */}
      </Routes>
    </Router>
  )
}
```

### Testing Checklist

- [ ] Settings page loads without errors
- [ ] Current AI configuration displays correctly
- [ ] System prompt displays in read-only textarea
- [ ] Temperature slider updates value in real-time
- [ ] Max tokens input accepts valid range (256-4096)
- [ ] Model dropdown shows all available models
- [ ] Save button updates configuration
- [ ] Reset button reloads current settings
- [ ] Success/error messages display appropriately
- [ ] Settings persist during session
- [ ] Settings reset after application restart (expected behavior)

---

## Part 3: Chatbot Product Search Integration

### Goal

Enable the chatbot to actively search products and provide direct cart links during conversations.

### Implementation

#### 3.1 Add Product Search to ChatAgentService

**File:** `src/main/java/com/shopify/api/service/ChatAgentService.java`

**Inject ProductService:**
```java
@Autowired
private ProductService productService;
```

**Add product search detection:**
```java
public Mono<ChatMessage> processChat(ChatRequest chatRequest) {
    // Detect if user is asking about products
    String userMessage = chatRequest.getMessage().toLowerCase();
    boolean isProductQuery = userMessage.contains("gundam") ||
                            userMessage.contains("model") ||
                            userMessage.contains("product") ||
                            userMessage.contains("find") ||
                            userMessage.contains("search");

    if (isProductQuery) {
        // Search products and include in context
        Map<String, Object> searchResults = productService.searchProducts(chatRequest.getMessage(), 5, false);
        // Format products and include in system message or user context
    }

    // ... continue with API call
}
```

**Update system prompt to include product search capability:**
```text
You have access to search_products function. When a customer asks about specific models or products:
1. Call search_products with relevant keywords
2. Present the top results with name, price, and SKU
3. Generate cart links in this format: https://{SHOP_URL}/cart/VARIANT_ID:1

Example:
User: "Do you have any MG Gundam kits?"
You: "Yes! Here are some Master Grade Gundam kits we have:

1. MG RX-78-2 Gundam Ver 3.0 - $45.99 (SKU: MG-001)
   Add to cart: https://store.com/cart/12345:1

2. MG Gundam Wing Zero EW - $52.99 (SKU: MG-045)
   Add to cart: https://store.com/cart/12346:1
"
```

### Testing Checklist

- [ ] Chatbot detects product queries
- [ ] Search results included in AI context
- [ ] Cart links generated correctly
- [ ] Product information formatted properly
- [ ] Fallback to general response if no products found

---

## Implementation Order

### Week 1: Enhanced Search
1. Update ProductService.searchProducts() with advanced query syntax
2. Add includeArchived parameter to backend
3. Update ProductController endpoint
4. Update frontend ProductSearch.jsx with checkbox
5. Update api.js with new parameter
6. Test all search scenarios
7. Commit: "feat: add multi-field product search with archived filtering"

### Week 2: AI Configuration Backend
8. Add configuration properties to application.properties
9. Create external prompt file
10. Update ChatAgentService with configurable values
11. Create ConfigController for settings API
12. Test configuration endpoints
13. Commit: "feat: externalize AI configuration and add settings API"

### Week 3: Settings UI
14. Create SettingsPage.jsx component
15. Update Navigation and App routing
16. Update api.js with config methods
17. Test settings UI functionality
18. Commit: "feat: add settings UI for AI configuration"

### Week 4: Product Search Integration & Deploy
19. Add product search to ChatAgentService
20. Update system prompt with search instructions
21. Test chatbot product search
22. Full end-to-end testing
23. Build frontend: `./build-frontend.sh`
24. Build JAR: `mvn clean package`
25. Commit: "feat: integrate product search with chatbot"
26. Create tag: `git tag WORKING5`
27. Push and deploy: `git push origin main --tags`

---

## Success Criteria

### Enhanced Search
- ✅ Search returns results from title, body, tags, vendor
- ✅ Archived products excluded by default
- ✅ Users can toggle to include archived
- ✅ Status badges show for archived products
- ✅ Search response time < 2 seconds

### AI Configuration
- ✅ Settings UI accessible and functional
- ✅ Temperature, max tokens, model configurable
- ✅ System prompt visible in UI
- ✅ Changes apply immediately during session
- ✅ Configuration persists in application.properties

### Product Search Integration
- ✅ Chatbot searches products based on queries
- ✅ Cart links generated correctly
- ✅ Product information formatted clearly
- ✅ AI responses relevant and helpful

---

## Future Enhancements (Phase 3)

Based on user comment: *"once the basic functions are working we will add additional specialized agent to help the chat bot in giving more technical responses"*

### Specialized Agents (Later)
1. **Technical Specifications Agent** - Detailed kit information
2. **Inventory Management Agent** - Stock levels and availability
3. **Order History Agent** - Customer purchase history
4. **Pricing Strategy Agent** - Discount recommendations

**To Be Planned:** After WORKING5 is deployed and validated.

---

## Files Modified Summary

### Backend Files
- `src/main/java/com/shopify/api/service/ProductService.java` - Enhanced search
- `src/main/java/com/shopify/api/controller/ProductController.java` - Add includeArchived param
- `src/main/java/com/shopify/api/service/ChatAgentService.java` - Configurable AI + product search
- `src/main/java/com/shopify/api/controller/ConfigController.java` - NEW (settings API)
- `src/main/resources/application.properties` - AI configuration
- `src/main/resources/prompts/system-prompt.txt` - NEW (external prompt)

### Frontend Files
- `frontend/src/pages/ProductSearch.jsx` - Archived checkbox
- `frontend/src/pages/SettingsPage.jsx` - NEW (settings UI)
- `frontend/src/services/api.js` - New API methods
- `frontend/src/components/Navigation.jsx` - Settings link
- `frontend/src/App.jsx` - Settings route

---

**Last Updated:** 2025-10-11
**Status:** Ready for Implementation
**Next Action:** Begin Week 1 - Enhanced Search Implementation
