# Phase 3: Backend Enhancements
## System Prompt Optimization and Shop Configuration

**Duration:** 0.5-1 day (4-7 hours)

---

## Overview

Phase 3 enhances the backend AI service to:

1. Generate shop-specific system prompts
2. Format Claude responses as structured JSON with products
3. Add shop configuration API endpoints
4. Implement response parsing utilities
5. Optimize Claude tool use for product search

---

## Step 1: Enhanced System Prompt

### Update ChatAgentService

**File:** `/src/main/java/com/shopify/api/service/ChatAgentService.java`

Add method to generate shop-aware system prompt:

```java
/**
 * Generate shop-specific system prompt for product search assistant
 */
public String generateShopSystemPrompt(ShopifyShop shop) {
    String shopName = shop.getShopName() != null ? shop.getShopName() : shop.getShopDomain();
    String currency = shop.getCurrency() != null ? shop.getCurrency() : "USD";

    return String.format("""
        You are a knowledgeable and enthusiastic product assistant for %s, an online hobby store.

        ## Your Role
        - Help customers find the perfect products for their needs
        - Provide expert recommendations based on skill level, budget, and interests
        - Be friendly, patient, and passionate about hobbies

        ## Important Guidelines

        ### 1. Product Search
        - ALWAYS use the `search_products` tool when customers ask about products
        - Search for 3-5 relevant products to give customers good options
        - Consider synonyms and related terms (e.g., "gunpla" → "gundam model kit")

        ### 2. Response Format
        - Start with a friendly, conversational response
        - Explain why you recommend each product
        - Include key details: price, difficulty level, features
        - Format your response with clear sections

        ### 3. Product Recommendations
        - Provide 2-4 product options (not too many, not too few)
        - Include products at different price points when possible
        - Mention if items are in stock or out of stock
        - Highlight bestsellers or beginner-friendly options

        ### 4. Response Structure
        When recommending products, use this format:

        [Brief introductory text explaining your recommendations]

        **1. [Product Name]** - %s $[price]
        [Why this product is a good choice - 1-2 sentences]

        **2. [Product Name]** - %s $[price]
        [Why this product is a good choice - 1-2 sentences]

        [Closing question or offer to help more]

        ### 5. Additional Tips
        - Ask follow-up questions if the request is vague
        - Suggest complementary products when relevant
        - Share hobby tips and techniques
        - Be honest if a product is out of stock
        - Encourage customers to ask more questions

        ## Shop Information
        - Store Name: %s
        - Currency: %s
        - Specialties: Model kits, RC vehicles, hobby supplies, craft materials

        ## Example Interactions

        **Customer:** "I'm new to model building. What should I start with?"
        **You:** "Great question! For beginners, I recommend starting with simple High Grade (HG) Gundam kits. Let me show you some perfect starter options:

        **1. HG RX-78-2 Gundam** - %s $19.99
        This is the classic Gundam that started it all. It's beginner-friendly with clear instructions and doesn't require glue or paint. Great for learning basic techniques!

        **2. HG Barbatos Lupus** - %s $18.99
        From the Iron-Blooded Orphans series, this kit has fewer parts and builds quickly. Perfect for first-timers and looks amazing when finished!

        Both include color-coded plastic so they look great right out of the box. Would you like more information about either of these, or shall I suggest some tools to get started?"

        Remember: You're not just selling products - you're helping people discover and enjoy their hobbies!
        """,
        shopName, currency, currency, shopName, currency, currency, currency);
}
```

### Use Shop-Specific Prompt in Chat

Update `ChatAgentService.processChat()` to accept shop parameter:

```java
/**
 * Process chat with shop-specific configuration
 */
public Mono<Map<String, Object>> processChat(ChatRequest request, ShopifyShop shop) {
    // Generate shop-specific system prompt
    String systemPrompt = generateShopSystemPrompt(shop);

    // Override chatbot config system prompt
    ChatbotConfig config = chatbotConfigService.getConfig();
    config.setSystemPromptOverride(systemPrompt);
    chatbotConfigService.updateConfig(config);

    // Process chat as usual
    return processChat(request);
}
```

---

## Step 2: Structured JSON Responses

### Response Formatter Utility

**File:** `/src/main/java/com/shopify/api/util/ResponseFormatter.java`

```java
package com.shopify.api.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopify.api.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for formatting AI responses with structured product data
 */
public class ResponseFormatter {

    private static final Logger logger = LoggerFactory.getLogger(ResponseFormatter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Extract product IDs mentioned in Claude's response
     */
    public static List<String> extractProductIds(String claudeResponse) {
        // Look for product IDs in format: gid://shopify/Product/123456
        Pattern pattern = Pattern.compile("gid://shopify/Product/(\\d+)");
        Matcher matcher = pattern.matcher(claudeResponse);

        return matcher.results()
            .map(m -> m.group(0))
            .distinct()
            .toList();
    }

    /**
     * Format response with product cards
     */
    public static Map<String, Object> formatChatResponse(
            String claudeText,
            List<Product> products,
            String conversationId) {

        Map<String, Object> response = new HashMap<>();

        // Clean Claude response (remove tool use JSON if present)
        String cleanText = removeToolUseJson(claudeText);

        response.put("response", cleanText);
        response.put("products", formatProducts(products));
        response.put("conversationId", conversationId);
        response.put("timestamp", java.time.Instant.now().toString());

        return response;
    }

    /**
     * Remove Claude tool use JSON from response text
     */
    private static String removeToolUseJson(String text) {
        // Remove JSON blocks like: {"tool": "search_products", ...}
        return text.replaceAll("\\{[^}]*\"tool\"[^}]*\\}", "").trim();
    }

    /**
     * Format products for frontend consumption
     */
    private static List<Map<String, Object>> formatProducts(List<Product> products) {
        return products.stream().map(product -> {
            Map<String, Object> formatted = new HashMap<>();
            formatted.put("id", product.getId());
            formatted.put("title", product.getTitle());
            formatted.put("handle", product.getHandle());
            formatted.put("price", product.getPrice());
            formatted.put("currency", product.getCurrency());
            formatted.put("image", product.getFeaturedImage());
            formatted.put("url", product.getUrl());
            formatted.put("inStock", product.getInventoryQuantity() > 0);
            formatted.put("vendor", product.getVendor());
            formatted.put("tags", product.getTags());

            return formatted;
        }).toList();
    }

    /**
     * Format error response
     */
    public static Map<String, Object> formatErrorResponse(String error, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", error);
        response.put("message", message);
        response.put("timestamp", java.time.Instant.now().toString());
        return response;
    }
}
```

---

## Step 3: Shop Configuration API

### ShopifyShopController

**File:** `/src/main/java/com/shopify/api/controller/ShopifyShopController.java`

```java
package com.shopify.api.controller;

import com.shopify.api.model.ChatbotConfig;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.service.ShopifyShopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for shop management
 */
@RestController
@RequestMapping("/api/shopify/shops")
public class ShopifyShopController {

    private static final Logger logger = LoggerFactory.getLogger(ShopifyShopController.class);

    private final ShopifyShopService shopifyShopService;

    @Autowired
    public ShopifyShopController(ShopifyShopService shopifyShopService) {
        this.shopifyShopService = shopifyShopService;
    }

    /**
     * Get shop configuration
     * GET /api/shopify/shops/{shopDomain}
     */
    @GetMapping("/{shopDomain}")
    public ResponseEntity<Map<String, Object>> getShop(@PathVariable String shopDomain) {
        logger.info("Fetching shop configuration: {}", shopDomain);

        try {
            ShopifyShop shop = shopifyShopService.getShop(shopDomain);

            Map<String, Object> response = new HashMap<>();
            response.put("id", shop.getId());
            response.put("shopDomain", shop.getShopDomain());
            response.put("shopName", shop.getShopName());
            response.put("shopEmail", shop.getShopEmail());
            response.put("planName", shop.getPlanName());
            response.put("currency", shop.getCurrency());
            response.put("timezone", shop.getTimezone());

            // AI configuration
            Map<String, Object> aiConfig = new HashMap<>();
            aiConfig.put("enabled", shop.getAiEnabled());
            aiConfig.put("model", shop.getAiModel());
            aiConfig.put("temperature", shop.getAiTemperature());
            aiConfig.put("maxTokens", shop.getAiMaxTokens());
            aiConfig.put("systemPrompt", shop.getAiSystemPrompt());
            response.put("aiConfig", aiConfig);

            // Analytics
            Map<String, Object> analytics = new HashMap<>();
            analytics.put("enabled", shop.getAnalyticsEnabled());
            analytics.put("trackChatUsage", shop.getTrackChatUsage());
            response.put("analytics", analytics);

            response.put("installedAt", shop.getInstalledAt().toString());
            response.put("isActive", shop.getIsActive());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            logger.error("Shop not found: {}", shopDomain);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Shop not found");
            error.put("shopDomain", shopDomain);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update shop AI configuration
     * PUT /api/shopify/shops/{shopDomain}/config
     */
    @PutMapping("/{shopDomain}/config")
    public ResponseEntity<Map<String, Object>> updateShopConfig(
            @PathVariable String shopDomain,
            @RequestBody Map<String, Object> configUpdate) {

        logger.info("Updating shop configuration: {}", shopDomain);

        try {
            ShopifyShop shop = shopifyShopService.getShop(shopDomain);

            // Update AI configuration if provided
            if (configUpdate.containsKey("aiConfig")) {
                Map<String, Object> aiConfig = (Map<String, Object>) configUpdate.get("aiConfig");

                if (aiConfig.containsKey("enabled")) {
                    shop.setAiEnabled((Boolean) aiConfig.get("enabled"));
                }
                if (aiConfig.containsKey("model")) {
                    shop.setAiModel((String) aiConfig.get("model"));
                }
                if (aiConfig.containsKey("temperature")) {
                    Double temp = ((Number) aiConfig.get("temperature")).doubleValue();
                    shop.setAiTemperature(java.math.BigDecimal.valueOf(temp));
                }
                if (aiConfig.containsKey("maxTokens")) {
                    shop.setAiMaxTokens(((Number) aiConfig.get("maxTokens")).intValue());
                }
                if (aiConfig.containsKey("systemPrompt")) {
                    shop.setAiSystemPrompt((String) aiConfig.get("systemPrompt"));
                }
            }

            // Save updates
            ShopifyShop updatedShop = shopifyShopService.updateShop(shop);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Configuration updated successfully");
            response.put("config", updatedShop.toChatbotConfig());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            logger.error("Error updating shop config: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to update configuration");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * List all active shops (admin only)
     * GET /api/shopify/shops
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listShops() {
        logger.info("Listing all active shops");

        List<ShopifyShop> shops = shopifyShopService.getAllActiveShops();

        List<Map<String, Object>> shopList = shops.stream().map(shop -> {
            Map<String, Object> shopData = new HashMap<>();
            shopData.put("shopDomain", shop.getShopDomain());
            shopData.put("shopName", shop.getShopName());
            shopData.put("planName", shop.getPlanName());
            shopData.put("installedAt", shop.getInstalledAt().toString());
            shopData.put("isActive", shop.getIsActive());
            shopData.put("aiEnabled", shop.getAiEnabled());
            return shopData;
        }).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("shops", shopList);
        response.put("total", shopList.size());

        return ResponseEntity.ok(response);
    }
}
```

Add `updateShop()` method to `ShopifyShopService`:

```java
@Transactional
public ShopifyShop updateShop(ShopifyShop shop) {
    return shopifyShopRepository.save(shop);
}
```

---

## Step 4: Update Chat Controller

### Use Shop-Specific Processing

Update `ShopifyChatController.sendMessage()`:

```java
@PostMapping("/message")
public Mono<ResponseEntity<Map<String, Object>>> sendMessage(
        @RequestParam String shop,
        @RequestBody ChatRequest request) {

    logger.info("Chat message received for shop: {}", shop);

    try {
        if (!shopifyShopService.isShopActive(shop)) {
            logger.warn("Shop not found or inactive: {}", shop);
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ResponseFormatter.formatErrorResponse(
                    "Shop not found or inactive",
                    shop)));
        }

        ShopifyShop shopConfig = shopifyShopService.getShop(shop);

        if (!shopConfig.getAiEnabled()) {
            logger.warn("AI disabled for shop: {}", shop);
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ResponseFormatter.formatErrorResponse(
                    "AI assistant is disabled",
                    "Contact shop administrator to enable")));
        }

        // Process chat with shop-specific configuration
        return chatAgentService.processChat(request, shopConfig)
            .map(ResponseEntity::ok)
            .onErrorResume(e -> {
                logger.error("Error processing chat for shop {}: {}", shop, e.getMessage());
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseFormatter.formatErrorResponse(
                        "Chat processing failed",
                        e.getMessage())));
            });

    } catch (Exception e) {
        logger.error("Error in sendMessage: {}", e.getMessage(), e);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ResponseFormatter.formatErrorResponse(
                "Internal server error",
                e.getMessage())));
    }
}
```

---

## Step 5: Testing

### Test Shop Configuration API

**Get shop config:**
```bash
curl http://localhost:8080/api/shopify/shops/hearnshobbies.myshopify.com
```

**Update AI config:**
```bash
curl -X PUT http://localhost:8080/api/shopify/shops/hearnshobbies.myshopify.com/config \
  -H "Content-Type: application/json" \
  -d '{
    "aiConfig": {
      "enabled": true,
      "model": "claude-sonnet-4-5-20250929",
      "temperature": 0.8,
      "maxTokens": 8192
    }
  }'
```

### Test Shop-Specific Chat

```bash
curl -X POST "http://localhost:8080/api/shopify/chat/message?shop=hearnshobbies.myshopify.com" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Show me beginner Gundam kits",
    "conversationHistory": []
  }'
```

**Expected response:**
- Uses shop-specific system prompt
- Currency matches shop currency (USD)
- Shop name mentioned in response
- Products formatted correctly

---

## Phase 3 Checklist

- [ ] Shop-specific system prompt generator created
- [ ] System prompt includes shop name and currency
- [ ] Response formatter utility implemented
- [ ] Shop configuration API endpoints created
- [ ] Chat controller uses shop-specific processing
- [ ] Error responses properly formatted
- [ ] Shop config API tested (GET/PUT)
- [ ] Shop-specific chat tested
- [ ] Response includes structured product JSON
- [ ] System prompt optimized for product recommendations

---

## Next Phase

**Phase 4: Admin Product Search Assistant** - Build the admin dual-mode search page with AI integration.

---

*Last Updated: 2025-10-30*
*Next: 07-PHASE4-ADMIN-ASSISTANT.md*
