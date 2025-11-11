package com.shopify.api.controller;

import com.shopify.api.model.ChatAnalytics;
import com.shopify.api.model.ChatRequest;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.service.ChatAgentService;
import com.shopify.api.service.ChatAnalyticsService;
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
    private final ChatAnalyticsService chatAnalyticsService;

    @Autowired
    public ShopifyChatController(ChatAgentService chatAgentService,
                                  ShopifyShopService shopifyShopService,
                                  ChatAnalyticsService chatAnalyticsService) {
        this.chatAgentService = chatAgentService;
        this.shopifyShopService = shopifyShopService;
        this.chatAnalyticsService = chatAnalyticsService;
    }

    /**
     * Send message to AI assistant (shop-scoped)
     * POST /api/shopify/chat/message?shop=hearnshobbies.myshopify.com
     */
    @PostMapping("/message")
    public Mono<ResponseEntity<Map<String, Object>>> sendMessage(
            @RequestParam String shop,
            @RequestBody ChatRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Forwarded-For", required = false) String xForwardedFor,
            @RequestHeader(value = "X-Real-IP", required = false) String xRealIp,
            @RequestHeader(value = "Referer", required = false) String referer) {

        logger.info("Chat message received for shop: {} (session: {})", shop, request.getSessionId());

        // Start timer for response time tracking
        final long startTime = System.currentTimeMillis();

        try {
            // Validate shop is installed and active
            if (!shopifyShopService.isShopActive(shop)) {
                logger.warn("Shop not found or inactive: {}", shop);

                // Track failed interaction
                trackFailedInteraction(shop, request, "Shop not found or inactive", startTime);

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

                // Track failed interaction
                trackFailedInteraction(shop, request, "AI disabled for this shop", startTime);

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
                    // Calculate response time
                    long responseTime = System.currentTimeMillis() - startTime;

                    // Track successful interaction with token usage
                    trackSuccessfulInteraction(shopConfig, request, chatMessage.getContent(),
                        responseTime, chatMessage.getInputTokens(), chatMessage.getOutputTokens(),
                        userAgent, xForwardedFor, xRealIp, referer);

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

                    // Calculate response time for error case
                    long responseTime = System.currentTimeMillis() - startTime;

                    // Track failed interaction with error details
                    trackFailedInteractionWithShop(shopConfig, request,
                        "Chat processing failed: " + e.getMessage(), responseTime);

                    chatAgentService.clearShopContext();
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "Chat processing failed");
                    error.put("message", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
                });

        } catch (Exception e) {
            logger.error("Error in sendMessage: {}", e.getMessage(), e);

            // Calculate response time for exception case
            long responseTime = System.currentTimeMillis() - startTime;

            // Track failed interaction
            trackFailedInteraction(shop, request, "Internal server error: " + e.getMessage(),
                responseTime);

            Map<String, Object> error = new HashMap<>();
            error.put("error", "Internal server error");
            error.put("message", e.getMessage());
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
        }
    }

    /**
     * Track successful chat interaction in analytics database
     * Includes token usage and API cost calculation
     */
    private void trackSuccessfulInteraction(ShopifyShop shop, ChatRequest request,
                                           String assistantResponse, long responseTimeMs,
                                           Integer inputTokens, Integer outputTokens,
                                           String userAgent, String xForwardedFor,
                                           String xRealIp, String referer) {
        try {
            ChatAnalytics analytics = ChatAnalytics.builder()
                .shop(shop)
                .sessionId(request.getSessionId())
                .userIdentifier(request.getUserIdentifier())
                .userMessage(request.getMessage())
                .assistantResponse(assistantResponse)
                .modelUsed(shop.getAiModel())
                .temperatureUsed(shop.getAiTemperature())
                .maxTokensUsed(shop.getAiMaxTokens())
                .responseTimeMs((int) responseTimeMs)
                .wasSuccessful(true)
                .userAgent(userAgent)
                .ipAddress(extractClientIp(xForwardedFor, xRealIp))
                .refererUrl(referer)
                .build();

            // Calculate and set token usage and API cost
            if (inputTokens != null && outputTokens != null) {
                analytics.setTokensUsed(inputTokens + outputTokens);
                analytics.calculateApiCost(inputTokens, outputTokens);
                logger.debug("Token usage - Input: {}, Output: {}, Total: {}, Cost: ${}",
                    inputTokens, outputTokens, analytics.getTokensUsed(), analytics.getApiCostUsd());
            } else {
                logger.warn("Token usage not available from Claude API response");
            }

            chatAnalyticsService.trackInteraction(analytics);
            logger.debug("Analytics tracked for session: {}", request.getSessionId());
        } catch (Exception e) {
            // Don't fail the request if analytics tracking fails
            logger.error("Failed to track analytics: {}", e.getMessage());
        }
    }

    /**
     * Track failed interaction (shop not found case)
     */
    private void trackFailedInteraction(String shopDomain, ChatRequest request,
                                       String errorMessage, long responseTimeMs) {
        try {
            // Try to get shop, but it might not exist
            if (shopifyShopService.isShopActive(shopDomain)) {
                ShopifyShop shop = shopifyShopService.getShop(shopDomain);
                trackFailedInteractionWithShop(shop, request, errorMessage, responseTimeMs);
            } else {
                logger.debug("Cannot track analytics for inactive shop: {}", shopDomain);
            }
        } catch (Exception e) {
            logger.error("Failed to track failed interaction: {}", e.getMessage());
        }
    }

    /**
     * Track failed interaction (with shop object)
     */
    private void trackFailedInteractionWithShop(ShopifyShop shop, ChatRequest request,
                                               String errorMessage, long responseTimeMs) {
        try {
            ChatAnalytics analytics = ChatAnalytics.builder()
                .shop(shop)
                .sessionId(request.getSessionId())
                .userIdentifier(request.getUserIdentifier())
                .userMessage(request.getMessage())
                .modelUsed(shop.getAiModel())
                .temperatureUsed(shop.getAiTemperature())
                .maxTokensUsed(shop.getAiMaxTokens())
                .responseTimeMs((int) responseTimeMs)
                .wasSuccessful(false)
                .errorMessage(errorMessage)
                .build();

            chatAnalyticsService.trackInteraction(analytics);
            logger.debug("Failed analytics tracked for session: {}", request.getSessionId());
        } catch (Exception e) {
            logger.error("Failed to track failed analytics: {}", e.getMessage());
        }
    }

    /**
     * Extract client IP address from request headers
     * Handles common proxy headers (X-Forwarded-For, X-Real-IP)
     */
    private String extractClientIp(String xForwardedFor, String xRealIp) {
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }

        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return "unknown";
    }
}
