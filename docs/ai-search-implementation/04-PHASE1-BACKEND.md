# Phase 1: Backend Foundation
## OAuth, Database, and Shop-Scoped APIs

**Duration:** 1.5-2 days (10-14 hours)

---

## Overview

Phase 1 establishes the backend foundation for shop-scoped AI chat functionality:

1. Database migration for `shopify_shops` table
2. Shopify OAuth flow (install + callback)
3. Shop management services
4. Shop-scoped chat API
5. CORS configuration for Shopify storefronts

---

## Prerequisites

- Java 17+ installed
- PostgreSQL database running
- Shopify Partner account created
- Shopify custom app credentials obtained
- Anthropic API key configured

---

## Step 1: Database Migration

### Create Migration File

**File:** `/src/main/resources/db/migration/V006__create_shopify_shops.sql`

```sql
-- Migration: V006__create_shopify_shops.sql
-- Description: Create shopify_shops table for OAuth and shop-specific AI configuration

CREATE TABLE shopify_shops (
    id BIGSERIAL PRIMARY KEY,

    -- Shop identification
    shop_domain VARCHAR(255) NOT NULL UNIQUE,
    shop_name VARCHAR(255),
    shop_email VARCHAR(255),
    shop_owner VARCHAR(255),

    -- OAuth tokens (encrypt in production)
    access_token TEXT NOT NULL,
    scope TEXT,

    -- Shop metadata
    plan_name VARCHAR(100),
    currency VARCHAR(10) DEFAULT 'USD',
    timezone VARCHAR(100),

    -- AI configuration
    ai_enabled BOOLEAN DEFAULT true,
    ai_model VARCHAR(100) DEFAULT 'claude-sonnet-4-5-20250929',
    ai_temperature DECIMAL(3,2) DEFAULT 0.7,
    ai_max_tokens INTEGER DEFAULT 4096,
    ai_system_prompt TEXT,

    -- Analytics settings
    analytics_enabled BOOLEAN DEFAULT true,
    track_chat_usage BOOLEAN DEFAULT true,

    -- Installation tracking
    installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uninstalled_at TIMESTAMP,
    is_active BOOLEAN DEFAULT true,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_shopify_shops_domain ON shopify_shops(shop_domain);
CREATE INDEX idx_shopify_shops_active ON shopify_shops(is_active) WHERE is_active = true;
CREATE INDEX idx_shopify_shops_installed ON shopify_shops(installed_at);

-- Updated timestamp trigger
CREATE OR REPLACE FUNCTION update_shopify_shops_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_shopify_shops_updated_at
    BEFORE UPDATE ON shopify_shops
    FOR EACH ROW
    EXECUTE FUNCTION update_shopify_shops_updated_at();

-- Comments
COMMENT ON TABLE shopify_shops IS 'Stores Shopify shop OAuth tokens and AI configuration';
COMMENT ON COLUMN shopify_shops.access_token IS 'OAuth access token for Shopify API (encrypt in production)';
```

### Run Migration

```bash
# Spring Boot will run Flyway migrations automatically on startup
mvn spring-boot:run

# Or run migrations manually
mvn flyway:migrate
```

### Verify Migration

```sql
-- Connect to PostgreSQL
psql -U postgres -d your_database

-- Check table exists
\d shopify_shops

-- Check indexes
\di

-- Test insert
INSERT INTO shopify_shops (shop_domain, shop_name, access_token)
VALUES ('test-shop.myshopify.com', 'Test Shop', 'test_token');

-- Verify
SELECT * FROM shopify_shops WHERE shop_domain = 'test-shop.myshopify.com';

-- Cleanup
DELETE FROM shopify_shops WHERE shop_domain = 'test-shop.myshopify.com';
```

---

## Step 2: Entity and Repository

### ShopifyShop Entity

**File:** `/src/main/java/com/shopify/api/model/ShopifyShop.java`

```java
package com.shopify.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Shopify Shop entity - stores OAuth tokens and shop-specific configuration
 */
@Entity
@Table(name = "shopify_shops")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopifyShop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Shop identification
    @Column(name = "shop_domain", nullable = false, unique = true)
    private String shopDomain;

    @Column(name = "shop_name")
    private String shopName;

    @Column(name = "shop_email")
    private String shopEmail;

    @Column(name = "shop_owner")
    private String shopOwner;

    // OAuth tokens
    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "scope", columnDefinition = "TEXT")
    private String scope;

    // Shop metadata
    @Column(name = "plan_name", length = 100)
    private String planName;

    @Column(name = "currency", length = 10)
    private String currency = "USD";

    @Column(name = "timezone", length = 100)
    private String timezone;

    // AI configuration
    @Column(name = "ai_enabled")
    private Boolean aiEnabled = true;

    @Column(name = "ai_model", length = 100)
    private String aiModel = "claude-sonnet-4-5-20250929";

    @Column(name = "ai_temperature", precision = 3, scale = 2)
    private BigDecimal aiTemperature = new BigDecimal("0.70");

    @Column(name = "ai_max_tokens")
    private Integer aiMaxTokens = 4096;

    @Column(name = "ai_system_prompt", columnDefinition = "TEXT")
    private String aiSystemPrompt;

    // Analytics settings
    @Column(name = "analytics_enabled")
    private Boolean analyticsEnabled = true;

    @Column(name = "track_chat_usage")
    private Boolean trackChatUsage = true;

    // Installation tracking
    @Column(name = "installed_at", nullable = false)
    private LocalDateTime installedAt;

    @Column(name = "uninstalled_at")
    private LocalDateTime uninstalledAt;

    @Column(name = "is_active")
    private Boolean isActive = true;

    // Timestamps
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (installedAt == null) {
            installedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if shop is installed and active
     */
    public boolean isInstalled() {
        return isActive && uninstalledAt == null;
    }

    /**
     * Get AI configuration as a map for ChatbotConfig
     */
    public ChatbotConfig toChatbotConfig() {
        ChatbotConfig config = new ChatbotConfig();
        config.setModel(aiModel);
        config.setTemperature(aiTemperature.doubleValue());
        config.setMaxTokens(aiMaxTokens);

        if (aiSystemPrompt != null && !aiSystemPrompt.isBlank()) {
            config.setSystemPromptOverride(aiSystemPrompt);
        }

        return config;
    }
}
```

### ShopifyShop Repository

**File:** `/src/main/java/com/shopify/api/repository/ShopifyShopRepository.java`

```java
package com.shopify.api.repository;

import com.shopify.api.model.ShopifyShop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ShopifyShop entity
 */
@Repository
public interface ShopifyShopRepository extends JpaRepository<ShopifyShop, Long> {

    /**
     * Find shop by domain
     */
    Optional<ShopifyShop> findByShopDomain(String shopDomain);

    /**
     * Find all active shops
     */
    @Query("SELECT s FROM ShopifyShop s WHERE s.isActive = true AND s.uninstalledAt IS NULL")
    List<ShopifyShop> findAllActive();

    /**
     * Check if shop exists and is active
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM ShopifyShop s " +
           "WHERE s.shopDomain = :shopDomain AND s.isActive = true AND s.uninstalledAt IS NULL")
    boolean existsActiveShop(String shopDomain);

    /**
     * Find shops with AI enabled
     */
    @Query("SELECT s FROM ShopifyShop s WHERE s.isActive = true AND s.aiEnabled = true")
    List<ShopifyShop> findAllWithAiEnabled();
}
```

---

## Step 3: Shopify App Configuration

### Configuration Properties

**File:** `/src/main/resources/application.yml`

```yaml
# Add to existing application.yml

shopify:
  app:
    api-key: ${SHOPIFY_APP_API_KEY}
    api-secret: ${SHOPIFY_APP_API_SECRET}
    scopes: read_products,read_orders,write_script_tags
    redirect-uri: ${SHOPIFY_APP_REDIRECT_URI:https://your-app.railway.app/shopify/callback}
    install-uri: /shopify/install
    callback-uri: /shopify/callback
```

### Configuration Class

**File:** `/src/main/java/com/shopify/api/config/ShopifyAppConfig.java`

```java
package com.shopify.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Shopify app configuration properties
 */
@Configuration
@ConfigurationProperties(prefix = "shopify.app")
@Data
public class ShopifyAppConfig {

    private String apiKey;
    private String apiSecret;
    private String scopes;
    private String redirectUri;
    private String installUri;
    private String callbackUri;

    /**
     * Get OAuth authorization URL for a shop
     */
    public String getAuthorizationUrl(String shopDomain, String nonce) {
        return String.format(
            "https://%s/admin/oauth/authorize?client_id=%s&scope=%s&redirect_uri=%s&state=%s",
            shopDomain,
            apiKey,
            scopes,
            redirectUri,
            nonce
        );
    }

    /**
     * Get token exchange URL for a shop
     */
    public String getTokenExchangeUrl(String shopDomain) {
        return String.format("https://%s/admin/oauth/access_token", shopDomain);
    }
}
```

---

## Step 4: OAuth Service

### ShopifyOAuthService

**File:** `/src/main/java/com/shopify/api/service/ShopifyOAuthService.java`

```java
package com.shopify.api.service;

import com.shopify.api.config.ShopifyAppConfig;
import org.apache.commons.codec.digest.HmacUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Service for Shopify OAuth flow
 */
@Service
public class ShopifyOAuthService {

    private static final Logger logger = LoggerFactory.getLogger(ShopifyOAuthService.class);

    private final ShopifyAppConfig shopifyAppConfig;
    private final RestTemplate restTemplate;

    @Autowired
    public ShopifyOAuthService(ShopifyAppConfig shopifyAppConfig) {
        this.shopifyAppConfig = shopifyAppConfig;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Generate random nonce for OAuth state parameter
     */
    public String generateNonce() {
        return UUID.randomUUID().toString();
    }

    /**
     * Validate shop domain format
     */
    public boolean isValidShopDomain(String shopDomain) {
        if (shopDomain == null || shopDomain.isBlank()) {
            return false;
        }
        // Must end with .myshopify.com
        return shopDomain.matches("^[a-zA-Z0-9][a-zA-Z0-9\\-]*\\.myshopify\\.com$");
    }

    /**
     * Verify HMAC signature from Shopify callback
     */
    public boolean verifyHmac(Map<String, String> params, String hmac) {
        try {
            // Build query string without hmac parameter
            StringBuilder message = new StringBuilder();
            params.entrySet().stream()
                .filter(entry -> !entry.getKey().equals("hmac") && !entry.getKey().equals("signature"))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (message.length() > 0) {
                        message.append("&");
                    }
                    message.append(entry.getKey()).append("=").append(entry.getValue());
                });

            // Calculate HMAC-SHA256
            String calculatedHmac = new HmacUtils("HmacSHA256", shopifyAppConfig.getApiSecret())
                .hmacHex(message.toString());

            logger.debug("HMAC verification - Expected: {}, Calculated: {}", hmac, calculatedHmac);

            return calculatedHmac.equals(hmac);

        } catch (Exception e) {
            logger.error("HMAC verification failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Exchange authorization code for access token
     */
    public String exchangeCodeForToken(String shopDomain, String code) {
        try {
            String tokenUrl = shopifyAppConfig.getTokenExchangeUrl(shopDomain);

            // Build request body
            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("client_id", shopifyAppConfig.getApiKey());
            requestBody.add("client_secret", shopifyAppConfig.getApiSecret());
            requestBody.add("code", code);

            // Send POST request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);

            logger.info("Exchanging authorization code for access token: {}", shopDomain);

            ResponseEntity<Map> response = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                request,
                Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String accessToken = (String) response.getBody().get("access_token");
                logger.info("Successfully obtained access token for shop: {}", shopDomain);
                return accessToken;
            }

            logger.error("Failed to exchange code for token. Status: {}", response.getStatusCode());
            throw new RuntimeException("Token exchange failed");

        } catch (Exception e) {
            logger.error("Error exchanging code for token: {}", e.getMessage(), e);
            throw new RuntimeException("OAuth token exchange failed", e);
        }
    }

    /**
     * Fetch shop details from Shopify API
     */
    public Map<String, Object> fetchShopDetails(String shopDomain, String accessToken) {
        try {
            String shopUrl = String.format("https://%s/admin/api/2024-01/shop.json", shopDomain);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Shopify-Access-Token", accessToken);

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                shopUrl,
                HttpMethod.GET,
                request,
                Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (Map<String, Object>) response.getBody().get("shop");
            }

            logger.warn("Failed to fetch shop details for: {}", shopDomain);
            return Map.of();

        } catch (Exception e) {
            logger.error("Error fetching shop details: {}", e.getMessage());
            return Map.of();
        }
    }
}
```

---

## Step 5: Shop Management Service

### ShopifyShopService

**File:** `/src/main/java/com/shopify/api/service/ShopifyShopService.java`

```java
package com.shopify.api.service;

import com.shopify.api.model.ChatbotConfig;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.repository.ShopifyShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing Shopify shops
 */
@Service
public class ShopifyShopService {

    private static final Logger logger = LoggerFactory.getLogger(ShopifyShopService.class);

    private final ShopifyShopRepository shopifyShopRepository;
    private final ShopifyOAuthService shopifyOAuthService;

    @Autowired
    public ShopifyShopService(ShopifyShopRepository shopifyShopRepository,
                              ShopifyOAuthService shopifyOAuthService) {
        this.shopifyShopRepository = shopifyShopRepository;
        this.shopifyOAuthService = shopifyOAuthService;
    }

    /**
     * Save or update shop after OAuth installation
     */
    @Transactional
    public ShopifyShop saveShop(String shopDomain, String accessToken, String scope) {
        logger.info("Saving shop: {}", shopDomain);

        // Fetch shop details from Shopify
        Map<String, Object> shopDetails = shopifyOAuthService.fetchShopDetails(shopDomain, accessToken);

        // Check if shop already exists
        Optional<ShopifyShop> existingShop = shopifyShopRepository.findByShopDomain(shopDomain);

        ShopifyShop shop;
        if (existingShop.isPresent()) {
            // Update existing shop
            shop = existingShop.get();
            shop.setAccessToken(accessToken);
            shop.setScope(scope);
            shop.setIsActive(true);
            shop.setUninstalledAt(null); // Clear uninstall timestamp
            logger.info("Updating existing shop: {}", shopDomain);
        } else {
            // Create new shop
            shop = ShopifyShop.builder()
                .shopDomain(shopDomain)
                .accessToken(accessToken)
                .scope(scope)
                .isActive(true)
                .installedAt(LocalDateTime.now())
                .build();
            logger.info("Creating new shop: {}", shopDomain);
        }

        // Update shop details from Shopify API
        if (!shopDetails.isEmpty()) {
            shop.setShopName((String) shopDetails.get("name"));
            shop.setShopEmail((String) shopDetails.get("email"));
            shop.setShopOwner((String) shopDetails.get("shop_owner"));
            shop.setPlanName((String) shopDetails.get("plan_name"));
            shop.setCurrency((String) shopDetails.get("currency"));
            shop.setTimezone((String) shopDetails.get("timezone"));
        }

        return shopifyShopRepository.save(shop);
    }

    /**
     * Get shop by domain
     */
    public ShopifyShop getShop(String shopDomain) {
        return shopifyShopRepository.findByShopDomain(shopDomain)
            .orElseThrow(() -> new RuntimeException("Shop not found: " + shopDomain));
    }

    /**
     * Get shop by domain (optional)
     */
    public Optional<ShopifyShop> getShopOptional(String shopDomain) {
        return shopifyShopRepository.findByShopDomain(shopDomain);
    }

    /**
     * Check if shop is installed and active
     */
    public boolean isShopActive(String shopDomain) {
        return shopifyShopRepository.existsActiveShop(shopDomain);
    }

    /**
     * Get all active shops
     */
    public List<ShopifyShop> getAllActiveShops() {
        return shopifyShopRepository.findAllActive();
    }

    /**
     * Update shop AI configuration
     */
    @Transactional
    public ShopifyShop updateAiConfig(String shopDomain, ChatbotConfig config) {
        ShopifyShop shop = getShop(shopDomain);

        shop.setAiModel(config.getModel());
        shop.setAiTemperature(java.math.BigDecimal.valueOf(config.getTemperature()));
        shop.setAiMaxTokens(config.getMaxTokens());
        shop.setAiSystemPrompt(config.getSystemPromptOverride());

        logger.info("Updated AI config for shop: {}", shopDomain);

        return shopifyShopRepository.save(shop);
    }

    /**
     * Mark shop as uninstalled
     */
    @Transactional
    public void uninstallShop(String shopDomain) {
        Optional<ShopifyShop> shopOpt = shopifyShopRepository.findByShopDomain(shopDomain);

        if (shopOpt.isPresent()) {
            ShopifyShop shop = shopOpt.get();
            shop.setIsActive(false);
            shop.setUninstalledAt(LocalDateTime.now());
            shopifyShopRepository.save(shop);
            logger.info("Shop uninstalled: {}", shopDomain);
        }
    }
}
```

---

## Step 6: OAuth Controllers

### ShopifyInstallController

**File:** `/src/main/java/com/shopify/api/controller/ShopifyInstallController.java`

```java
package com.shopify.api.controller;

import com.shopify.api.config.ShopifyAppConfig;
import com.shopify.api.service.ShopifyOAuthService;
import com.shopify.api.service.ShopifyShopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for Shopify app installation (OAuth flow)
 */
@Controller
@RequestMapping("/shopify")
public class ShopifyInstallController {

    private static final Logger logger = LoggerFactory.getLogger(ShopifyInstallController.class);

    private final ShopifyAppConfig shopifyAppConfig;
    private final ShopifyOAuthService shopifyOAuthService;
    private final ShopifyShopService shopifyShopService;

    @Autowired
    public ShopifyInstallController(ShopifyAppConfig shopifyAppConfig,
                                     ShopifyOAuthService shopifyOAuthService,
                                     ShopifyShopService shopifyShopService) {
        this.shopifyAppConfig = shopifyAppConfig;
        this.shopifyOAuthService = shopifyOAuthService;
        this.shopifyShopService = shopifyShopService;
    }

    /**
     * Step 1: Install app (redirect to Shopify OAuth)
     * GET /shopify/install?shop=hearnshobbies.myshopify.com
     */
    @GetMapping("/install")
    public RedirectView installApp(@RequestParam String shop, HttpSession session) {
        logger.info("Installing app for shop: {}", shop);

        // Validate shop domain
        if (!shopifyOAuthService.isValidShopDomain(shop)) {
            logger.error("Invalid shop domain: {}", shop);
            return new RedirectView("/error?message=Invalid shop domain");
        }

        // Generate nonce for CSRF protection
        String nonce = shopifyOAuthService.generateNonce();
        session.setAttribute("oauth_nonce", nonce);
        session.setAttribute("oauth_shop", shop);

        logger.debug("Generated nonce: {}", nonce);

        // Redirect to Shopify OAuth authorization
        String authUrl = shopifyAppConfig.getAuthorizationUrl(shop, nonce);
        return new RedirectView(authUrl);
    }

    /**
     * Step 2: OAuth callback (Shopify redirects here after approval)
     * GET /shopify/callback?shop=...&code=...&state=...&hmac=...
     */
    @GetMapping("/callback")
    public RedirectView authCallback(@RequestParam String shop,
                                      @RequestParam String code,
                                      @RequestParam String state,
                                      @RequestParam String hmac,
                                      @RequestParam(required = false) String timestamp,
                                      HttpSession session) {
        logger.info("OAuth callback received for shop: {}", shop);

        try {
            // Verify nonce (CSRF protection)
            String storedNonce = (String) session.getAttribute("oauth_nonce");
            String storedShop = (String) session.getAttribute("oauth_shop");

            if (storedNonce == null || !storedNonce.equals(state)) {
                logger.error("Invalid nonce. Expected: {}, Got: {}", storedNonce, state);
                return new RedirectView("/error?message=Invalid state parameter");
            }

            if (!shop.equals(storedShop)) {
                logger.error("Shop mismatch. Expected: {}, Got: {}", storedShop, shop);
                return new RedirectView("/error?message=Shop mismatch");
            }

            // Verify HMAC signature
            Map<String, String> params = new HashMap<>();
            params.put("shop", shop);
            params.put("code", code);
            params.put("state", state);
            if (timestamp != null) {
                params.put("timestamp", timestamp);
            }

            if (!shopifyOAuthService.verifyHmac(params, hmac)) {
                logger.error("HMAC verification failed for shop: {}", shop);
                return new RedirectView("/error?message=HMAC verification failed");
            }

            // Exchange code for access token
            String accessToken = shopifyOAuthService.exchangeCodeForToken(shop, code);

            // Save shop to database
            shopifyShopService.saveShop(shop, accessToken, shopifyAppConfig.getScopes());

            // Clear session
            session.removeAttribute("oauth_nonce");
            session.removeAttribute("oauth_shop");

            logger.info("Successfully installed app for shop: {}", shop);

            // Redirect to admin dashboard
            return new RedirectView("/admin?shop=" + shop + "&installed=true");

        } catch (Exception e) {
            logger.error("OAuth callback error: {}", e.getMessage(), e);
            return new RedirectView("/error?message=Installation failed");
        }
    }
}
```

---

## Step 7: Shop-Scoped Chat API

### ShopifyChatController

**File:** `/src/main/java/com/shopify/api/controller/ShopifyChatController.java`

```java
package com.shopify.api.controller;

import com.shopify.api.model.ChatRequest;
import com.shopify.api.model.ChatbotConfig;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.service.ChatAgentService;
import com.shopify.api.service.ChatbotConfigService;
import com.shopify.api.service.ShopifyShopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API for shop-scoped chat with AI assistant
 */
@RestController
@RequestMapping("/api/shopify/chat")
public class ShopifyChatController {

    private static final Logger logger = LoggerFactory.getLogger(ShopifyChatController.class);

    private final ChatAgentService chatAgentService;
    private final ChatbotConfigService chatbotConfigService;
    private final ShopifyShopService shopifyShopService;

    @Autowired
    public ShopifyChatController(ChatAgentService chatAgentService,
                                  ChatbotConfigService chatbotConfigService,
                                  ShopifyShopService shopifyShopService) {
        this.chatAgentService = chatAgentService;
        this.chatbotConfigService = chatbotConfigService;
        this.shopifyShopService = shopifyShopService;
    }

    /**
     * Send message to AI assistant (shop-scoped)
     * POST /api/shopify/chat/message?shop=hearnshobbies.myshopify.com
     */
    @PostMapping("/message")
    public Mono<ResponseEntity<Map<String, Object>>> sendMessage(
            @RequestParam String shop,
            @RequestBody ChatRequest request) {

        logger.info("Chat message received for shop: {}", shop);

        try {
            // Validate shop is installed and active
            if (!shopifyShopService.isShopActive(shop)) {
                logger.warn("Shop not found or inactive: {}", shop);
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Shop not found or inactive");
                error.put("shop", shop);
                return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(error));
            }

            // Get shop configuration
            ShopifyShop shopConfig = shopifyShopService.getShop(shop);

            // Check if AI is enabled for this shop
            if (!shopConfig.getAiEnabled()) {
                logger.warn("AI disabled for shop: {}", shop);
                Map<String, Object> error = new HashMap<>();
                error.put("error", "AI assistant is disabled for this shop");
                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(error));
            }

            // Apply shop-specific AI configuration
            ChatbotConfig shopChatConfig = shopConfig.toChatbotConfig();
            chatbotConfigService.updateConfig(shopChatConfig);

            logger.debug("Applied shop-specific config: model={}, temp={}, maxTokens={}",
                shopChatConfig.getModel(),
                shopChatConfig.getTemperature(),
                shopChatConfig.getMaxTokens());

            // Process chat request
            return chatAgentService.processChat(request)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    logger.error("Error processing chat for shop {}: {}", shop, e.getMessage());
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "Chat processing failed");
                    error.put("message", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
                });

        } catch (Exception e) {
            logger.error("Error in sendMessage: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Internal server error");
            error.put("message", e.getMessage());
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
        }
    }
}
```

---

## Step 8: CORS Configuration

### Update WebConfig

**File:** `/src/main/java/com/shopify/api/config/WebConfig.java`

```java
package com.shopify.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration - CORS settings
 */
@Configuration
public class WebConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    // Allow requests from Shopify storefronts
                    .allowedOrigins(
                        "https://hearnshobbies.com",
                        "https://*.myshopify.com",
                        "http://localhost:5173" // Development
                    )
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);

                // OAuth endpoints (no credentials needed)
                registry.addMapping("/shopify/**")
                    .allowedOrigins("https://*.myshopify.com")
                    .allowedMethods("GET", "POST")
                    .allowedHeaders("*")
                    .maxAge(3600);
            }
        };
    }
}
```

---

## Step 9: Environment Variables

### Update application.yml

```yaml
# Add Shopify app credentials
shopify:
  app:
    api-key: ${SHOPIFY_APP_API_KEY}
    api-secret: ${SHOPIFY_APP_API_SECRET}
    scopes: read_products,read_orders,write_script_tags
    redirect-uri: ${SHOPIFY_APP_REDIRECT_URI:https://your-app.railway.app/shopify/callback}
```

### Set Environment Variables (Railway)

```bash
# In Railway dashboard, add these environment variables:
SHOPIFY_APP_API_KEY=your_shopify_api_key
SHOPIFY_APP_API_SECRET=your_shopify_api_secret
SHOPIFY_APP_REDIRECT_URI=https://your-app.railway.app/shopify/callback
```

---

## Step 10: Testing

### Test OAuth Flow (Development)

1. **Start backend:**
```bash
mvn spring-boot:run
```

2. **Open install URL:**
```
http://localhost:8080/shopify/install?shop=hearnshobbies.myshopify.com
```

3. **You should be redirected to Shopify OAuth page**
4. **After approving, you'll be redirected to callback**
5. **Check database for new shop record:**

```sql
SELECT * FROM shopify_shops WHERE shop_domain = 'hearnshobbies.myshopify.com';
```

### Test Chat API (Postman)

```http
POST http://localhost:8080/api/shopify/chat/message?shop=hearnshobbies.myshopify.com
Content-Type: application/json

{
  "message": "Show me Gundam model kits",
  "conversationHistory": []
}
```

**Expected response:**
- AI response with product recommendations
- Product cards with images, prices, URLs
- Conversation ID for tracking

### Test Shop Configuration

```http
GET http://localhost:8080/api/shopify/shops/hearnshobbies.myshopify.com
```

---

## Deployment to Railway

### Build and Deploy

```bash
# Build project
mvn clean package -DskipTests

# Railway will automatically deploy from main branch
git add .
git commit -m "Phase 1: Backend foundation with OAuth and shop-scoped chat"
git push origin main
```

### Verify Deployment

1. Check Railway logs for startup
2. Verify database migrations ran
3. Test OAuth install URL: `https://your-app.railway.app/shopify/install?shop=test.myshopify.com`
4. Test chat API with Postman

---

## Troubleshooting

### Issue: HMAC Verification Fails

**Cause:** API secret mismatch or query parameter encoding

**Solution:**
- Double-check `SHOPIFY_APP_API_SECRET` in Railway
- Ensure query parameters are not URL-encoded twice
- Log the calculated HMAC vs expected HMAC

### Issue: Token Exchange Fails

**Cause:** Redirect URI mismatch

**Solution:**
- Verify `SHOPIFY_APP_REDIRECT_URI` matches Shopify Partner Dashboard
- Must use HTTPS in production
- No trailing slash in URI

### Issue: Shop Not Found

**Cause:** Shop not saved to database after OAuth

**Solution:**
- Check database logs for errors
- Verify Flyway migrations ran successfully
- Check Shopify API credentials

---

## Phase 1 Checklist

- [ ] Database migration runs successfully
- [ ] `shopify_shops` table created with indexes
- [ ] Entity and repository created and tested
- [ ] OAuth install flow redirects to Shopify
- [ ] OAuth callback verifies HMAC signature
- [ ] Access token exchange successful
- [ ] Shop details saved to database
- [ ] Shop-scoped chat API accepts shop parameter
- [ ] AI configuration applied per shop
- [ ] CORS configured for Shopify storefronts
- [ ] Environment variables set in Railway
- [ ] Backend deployed and accessible
- [ ] OAuth flow tested end-to-end
- [ ] Chat API tested with Postman

---

## Next Phase

**Phase 2: Shopify Theme Extension** - Create the search bar enhancer widget and AI chat modal.

---

*Last Updated: 2025-10-30*
*Next: 05-PHASE2-THEME-EXTENSION.md*
