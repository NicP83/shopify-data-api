package com.shopify.api.controller;

import com.shopify.api.model.ShopifyShop;
import com.shopify.api.model.SystemPrompt;
import com.shopify.api.service.ShopifyShopService;
import com.shopify.api.service.SystemPromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for shop configuration management
 * Allows shops to view and update their AI settings, prompts, and preferences
 */
@RestController
@RequestMapping("/api/shopify/config")
public class ShopConfigController {

    private static final Logger logger = LoggerFactory.getLogger(ShopConfigController.class);

    private final ShopifyShopService shopifyShopService;
    private final SystemPromptService systemPromptService;

    @Autowired
    public ShopConfigController(ShopifyShopService shopifyShopService,
                                 SystemPromptService systemPromptService) {
        this.shopifyShopService = shopifyShopService;
        this.systemPromptService = systemPromptService;
    }

    /**
     * Get shop configuration
     * GET /api/shopify/config?shop=hearnshobbies.myshopify.com
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getShopConfig(@RequestParam String shop) {
        logger.info("Fetching configuration for shop: {}", shop);

        try {
            if (!shopifyShopService.isShopActive(shop)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Shop not found or inactive"));
            }

            ShopifyShop shopConfig = shopifyShopService.getShop(shop);

            Map<String, Object> config = new HashMap<>();
            config.put("shopDomain", shopConfig.getShopDomain());
            config.put("shopName", shopConfig.getShopName());
            config.put("isActive", shopConfig.getIsActive());

            // AI Configuration
            Map<String, Object> aiConfig = new HashMap<>();
            aiConfig.put("enabled", shopConfig.getAiEnabled());
            aiConfig.put("model", shopConfig.getAiModel());
            aiConfig.put("temperature", shopConfig.getAiTemperature());
            aiConfig.put("maxTokens", shopConfig.getAiMaxTokens());
            config.put("ai", aiConfig);

            // Analytics Configuration
            Map<String, Object> analyticsConfig = new HashMap<>();
            analyticsConfig.put("enabled", shopConfig.getAnalyticsEnabled());
            config.put("analytics", analyticsConfig);

            // Installation info
            config.put("installedAt", shopConfig.getInstalledAt());
            config.put("scope", shopConfig.getScope());

            return ResponseEntity.ok(config);

        } catch (Exception e) {
            logger.error("Error fetching shop config: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch configuration"));
        }
    }

    /**
     * Update shop AI configuration
     * PUT /api/shopify/config/ai?shop=hearnshobbies.myshopify.com
     */
    @PutMapping("/ai")
    public ResponseEntity<Map<String, Object>> updateAiConfig(
            @RequestParam String shop,
            @RequestBody Map<String, Object> aiConfig) {

        logger.info("Updating AI configuration for shop: {}", shop);

        try {
            if (!shopifyShopService.isShopActive(shop)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Shop not found or inactive"));
            }

            ShopifyShop shopConfig = shopifyShopService.getShop(shop);

            // Update AI settings
            if (aiConfig.containsKey("enabled")) {
                shopConfig.setAiEnabled((Boolean) aiConfig.get("enabled"));
            }
            if (aiConfig.containsKey("model")) {
                shopConfig.setAiModel((String) aiConfig.get("model"));
            }
            if (aiConfig.containsKey("temperature")) {
                Object temp = aiConfig.get("temperature");
                if (temp instanceof Number) {
                    shopConfig.setAiTemperature(java.math.BigDecimal.valueOf(((Number) temp).doubleValue()));
                }
            }
            if (aiConfig.containsKey("maxTokens")) {
                shopConfig.setAiMaxTokens((Integer) aiConfig.get("maxTokens"));
            }

            shopifyShopService.updateShop(shopConfig);

            logger.info("AI configuration updated for shop: {}", shop);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "AI configuration updated",
                "ai", Map.of(
                    "enabled", shopConfig.getAiEnabled(),
                    "model", shopConfig.getAiModel(),
                    "temperature", shopConfig.getAiTemperature(),
                    "maxTokens", shopConfig.getAiMaxTokens()
                )
            ));

        } catch (Exception e) {
            logger.error("Error updating AI config: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to update AI configuration"));
        }
    }

    /**
     * Get active system prompts for shop
     * GET /api/shopify/config/prompts?shop=hearnshobbies.myshopify.com
     */
    @GetMapping("/prompts")
    public ResponseEntity<Map<String, Object>> getShopPrompts(@RequestParam String shop) {
        logger.info("Fetching prompts for shop: {}", shop);

        try {
            if (!shopifyShopService.isShopActive(shop)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Shop not found or inactive"));
            }

            ShopifyShop shopConfig = shopifyShopService.getShop(shop);
            List<SystemPrompt> prompts = systemPromptService.getAllActivePrompts(shopConfig);

            List<Map<String, Object>> promptsList = prompts.stream()
                .map(prompt -> {
                    Map<String, Object> p = new HashMap<>();
                    p.put("id", prompt.getId());
                    p.put("name", prompt.getPromptName());
                    p.put("type", prompt.getPromptType());
                    p.put("description", prompt.getPromptDescription());
                    p.put("version", prompt.getVersion());
                    p.put("isGlobal", prompt.isGlobal());
                    p.put("usageCount", prompt.getUsageCount());
                    p.put("successRate", prompt.getSuccessRate());
                    p.put("avgResponseTime", prompt.getAvgResponseTimeMs());
                    return p;
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "prompts", promptsList,
                "count", promptsList.size()
            ));

        } catch (Exception e) {
            logger.error("Error fetching prompts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch prompts"));
        }
    }

    /**
     * Get specific prompt details including full text
     * GET /api/shopify/config/prompts/{promptId}?shop=hearnshobbies.myshopify.com
     */
    @GetMapping("/prompts/{promptId}")
    public ResponseEntity<Map<String, Object>> getPromptDetails(
            @PathVariable Long promptId,
            @RequestParam String shop) {

        logger.info("Fetching prompt {} for shop: {}", promptId, shop);

        try {
            if (!shopifyShopService.isShopActive(shop)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Shop not found or inactive"));
            }

            SystemPrompt prompt = systemPromptService.getPromptById(promptId)
                .orElseThrow(() -> new RuntimeException("Prompt not found"));

            Map<String, Object> promptDetails = new HashMap<>();
            promptDetails.put("id", prompt.getId());
            promptDetails.put("name", prompt.getPromptName());
            promptDetails.put("type", prompt.getPromptType());
            promptDetails.put("text", prompt.getPromptText());
            promptDetails.put("description", prompt.getPromptDescription());
            promptDetails.put("version", prompt.getVersion());
            promptDetails.put("isActive", prompt.getIsActive());
            promptDetails.put("isGlobal", prompt.isGlobal());
            promptDetails.put("usageCount", prompt.getUsageCount());
            promptDetails.put("successRate", prompt.getSuccessRate());
            promptDetails.put("avgResponseTime", prompt.getAvgResponseTimeMs());
            promptDetails.put("createdAt", prompt.getCreatedAt());
            promptDetails.put("updatedAt", prompt.getUpdatedAt());

            return ResponseEntity.ok(promptDetails);

        } catch (Exception e) {
            logger.error("Error fetching prompt details: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch prompt details"));
        }
    }

    /**
     * Create shop-specific prompt from global template
     * POST /api/shopify/config/prompts/customize?shop=hearnshobbies.myshopify.com
     */
    @PostMapping("/prompts/customize")
    public ResponseEntity<Map<String, Object>> customizePrompt(
            @RequestParam String shop,
            @RequestBody Map<String, String> request) {

        logger.info("Creating customized prompt for shop: {}", shop);

        try {
            if (!shopifyShopService.isShopActive(shop)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Shop not found or inactive"));
            }

            String globalPromptName = request.get("globalPromptName");
            String customizations = request.get("customizations");

            if (globalPromptName == null || globalPromptName.isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "globalPromptName is required"));
            }

            ShopifyShop shopConfig = shopifyShopService.getShop(shop);
            SystemPrompt customPrompt = systemPromptService.createShopPromptFromGlobal(
                globalPromptName,
                shopConfig,
                customizations
            );

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Custom prompt created",
                "prompt", Map.of(
                    "id", customPrompt.getId(),
                    "name", customPrompt.getPromptName(),
                    "type", customPrompt.getPromptType(),
                    "version", customPrompt.getVersion()
                )
            ));

        } catch (Exception e) {
            logger.error("Error creating custom prompt: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to create custom prompt"));
        }
    }

    /**
     * Update existing system prompt (creates new version)
     * PUT /api/shopify/config/prompts/{promptId}?shop=hearnshobbies.myshopify.com
     */
    @PutMapping("/prompts/{promptId}")
    public ResponseEntity<Map<String, Object>> updatePrompt(
            @PathVariable Long promptId,
            @RequestParam String shop,
            @RequestBody Map<String, String> request) {

        logger.info("Updating prompt {} for shop: {}", promptId, shop);

        try {
            if (!shopifyShopService.isShopActive(shop)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Shop not found or inactive"));
            }

            String newPromptText = request.get("promptText");
            String description = request.get("description");

            if (newPromptText == null || newPromptText.isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "promptText is required"));
            }

            SystemPrompt updatedPrompt = systemPromptService.updatePrompt(
                promptId,
                newPromptText,
                description
            );

            logger.info("Prompt updated successfully - new version: {}", updatedPrompt.getVersion());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Prompt updated successfully",
                "prompt", Map.of(
                    "id", updatedPrompt.getId(),
                    "name", updatedPrompt.getPromptName(),
                    "type", updatedPrompt.getPromptType(),
                    "version", updatedPrompt.getVersion(),
                    "description", updatedPrompt.getPromptDescription()
                )
            ));

        } catch (Exception e) {
            logger.error("Error updating prompt: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to update prompt: " + e.getMessage()));
        }
    }

    /**
     * Update shop analytics settings
     * PUT /api/shopify/config/analytics?shop=hearnshobbies.myshopify.com
     */
    @PutMapping("/analytics")
    public ResponseEntity<Map<String, Object>> updateAnalyticsConfig(
            @RequestParam String shop,
            @RequestBody Map<String, Boolean> analyticsConfig) {

        logger.info("Updating analytics configuration for shop: {}", shop);

        try {
            if (!shopifyShopService.isShopActive(shop)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Shop not found or inactive"));
            }

            ShopifyShop shopConfig = shopifyShopService.getShop(shop);

            if (analyticsConfig.containsKey("enabled")) {
                shopConfig.setAnalyticsEnabled(analyticsConfig.get("enabled"));
            }

            shopifyShopService.updateShop(shopConfig);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Analytics configuration updated",
                "analytics", Map.of("enabled", shopConfig.getAnalyticsEnabled())
            ));

        } catch (Exception e) {
            logger.error("Error updating analytics config: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to update analytics configuration"));
        }
    }
}
