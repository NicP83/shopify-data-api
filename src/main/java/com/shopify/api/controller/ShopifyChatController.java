package com.shopify.api.controller;

import com.shopify.api.model.ChatRequest;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.service.ChatAgentService;
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
    private final ShopifyShopService shopifyShopService;

    @Autowired
    public ShopifyChatController(ChatAgentService chatAgentService,
                                  ShopifyShopService shopifyShopService) {
        this.chatAgentService = chatAgentService;
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

            // Apply shop-specific AI configuration to ChatAgentService
            chatAgentService.setAnthropicModel(shopConfig.getAiModel());
            chatAgentService.setTemperature(shopConfig.getAiTemperatureValue());
            chatAgentService.setMaxTokens(shopConfig.getAiMaxTokensValue());

            // Set shop context for dynamic system prompts
            chatAgentService.setShopContext(shopConfig);

            logger.debug("Applied shop-specific config: model={}, temp={}, maxTokens={}",
                shopConfig.getAiModel(),
                shopConfig.getAiTemperatureValue(),
                shopConfig.getAiMaxTokensValue());

            // Process chat request with shop-specific settings
            return chatAgentService.processChat(request)
                .map(chatMessage -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("response", chatMessage.getContent());
                    response.put("role", chatMessage.getRole());
                    response.put("timestamp", java.time.Instant.now().toString());
                    return ResponseEntity.ok(response);
                })
                .doFinally(signalType -> {
                    // Clear shop context after request completes
                    chatAgentService.clearShopContext();
                    logger.debug("Cleared shop context for: {}", shop);
                })
                .onErrorResume(e -> {
                    logger.error("Error processing chat for shop {}: {}", shop, e.getMessage());
                    chatAgentService.clearShopContext();
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
