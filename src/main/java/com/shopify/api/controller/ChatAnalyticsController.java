package com.shopify.api.controller;

import com.shopify.api.model.ApiResponse;
import com.shopify.api.model.ChatAnalytics;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.service.ChatAnalyticsService;
import com.shopify.api.service.ShopifyShopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Chat Analytics
 * Provides endpoints for viewing and analyzing chat interactions
 */
@RestController
@RequestMapping("/api/analytics/chat")
public class ChatAnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(ChatAnalyticsController.class);

    private final ChatAnalyticsService chatAnalyticsService;
    private final ShopifyShopService shopifyShopService;

    @Autowired
    public ChatAnalyticsController(ChatAnalyticsService chatAnalyticsService,
                                    ShopifyShopService shopifyShopService) {
        this.chatAnalyticsService = chatAnalyticsService;
        this.shopifyShopService = shopifyShopService;
    }

    /**
     * GET /api/analytics/chat/summary?shop={shop_domain}
     * Get analytics summary for a shop
     *
     * @param shop Shop domain (e.g., hearnshobbies.myshopify.com)
     * @return Analytics summary with metrics
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnalyticsSummary(
            @RequestParam String shop) {

        logger.info("GET /api/analytics/chat/summary - shop: {}", shop);

        try {
            if (!shopifyShopService.isShopActive(shop)) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Shop not found", "Shop is not registered or inactive"));
            }

            ShopifyShop shopConfig = shopifyShopService.getShop(shop);
            Map<String, Object> summary = chatAnalyticsService.getShopAnalyticsSummary(shopConfig);

            return ResponseEntity.ok(ApiResponse.success(summary));
        } catch (Exception e) {
            logger.error("Error fetching chat analytics summary for shop {}", shop, e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to fetch analytics summary", e.getMessage()));
        }
    }

    /**
     * GET /api/analytics/chat/daily?shop={shop}&days={days}
     * Get daily interaction statistics
     *
     * @param shop Shop domain
     * @param days Number of days to look back (default: 30)
     * @return Daily interaction counts
     */
    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getDailyStats(
            @RequestParam String shop,
            @RequestParam(defaultValue = "30") int days) {

        logger.info("GET /api/analytics/chat/daily - shop: {}, days: {}", shop, days);

        try {
            if (!shopifyShopService.isShopActive(shop)) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Shop not found", "Shop is not registered or inactive"));
            }

            ShopifyShop shopConfig = shopifyShopService.getShop(shop);
            List<Map<String, Object>> dailyStats = chatAnalyticsService.getDailyStats(shopConfig, days);

            return ResponseEntity.ok(ApiResponse.success(dailyStats));
        } catch (Exception e) {
            logger.error("Error fetching daily stats for shop {}", shop, e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to fetch daily stats", e.getMessage()));
        }
    }

    /**
     * GET /api/analytics/chat/session/{sessionId}
     * Get full conversation history for a session
     *
     * @param sessionId Session identifier
     * @return List of interactions in chronological order
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<ApiResponse<List<ChatAnalytics>>> getSessionHistory(
            @PathVariable String sessionId) {

        logger.info("GET /api/analytics/chat/session/{}", sessionId);

        try {
            List<ChatAnalytics> sessionHistory = chatAnalyticsService.getSessionHistory(sessionId);
            return ResponseEntity.ok(ApiResponse.success(sessionHistory));
        } catch (Exception e) {
            logger.error("Error fetching session history for {}", sessionId, e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to fetch session history", e.getMessage()));
        }
    }

    /**
     * GET /api/analytics/chat/recent?shop={shop}&limit={limit}
     * Get recent interactions for a shop
     *
     * @param shop Shop domain
     * @param limit Number of interactions to return (default: 50)
     * @return Recent interactions
     */
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<ChatAnalytics>>> getRecentInteractions(
            @RequestParam String shop,
            @RequestParam(defaultValue = "50") int limit) {

        logger.info("GET /api/analytics/chat/recent - shop: {}, limit: {}", shop, limit);

        try {
            if (!shopifyShopService.isShopActive(shop)) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Shop not found", "Shop is not registered or inactive"));
            }

            ShopifyShop shopConfig = shopifyShopService.getShop(shop);

            // Get recent interactions using date range (last 7 days)
            LocalDateTime startDate = LocalDateTime.now().minusDays(7);
            LocalDateTime endDate = LocalDateTime.now();
            List<ChatAnalytics> recentInteractions =
                chatAnalyticsService.getAnalyticsByDateRange(shopConfig, startDate, endDate);

            // Limit results
            if (recentInteractions.size() > limit) {
                recentInteractions = recentInteractions.subList(0, limit);
            }

            return ResponseEntity.ok(ApiResponse.success(recentInteractions));
        } catch (Exception e) {
            logger.error("Error fetching recent interactions for shop {}", shop, e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to fetch recent interactions", e.getMessage()));
        }
    }

    /**
     * GET /api/analytics/chat/top-errors?shop={shop}&limit={limit}
     * Get most common errors
     *
     * @param shop Shop domain
     * @param limit Number of top errors to return (default: 10)
     * @return List of [error_message, count] pairs
     */
    @GetMapping("/top-errors")
    public ResponseEntity<ApiResponse<List<Object[]>>> getTopErrors(
            @RequestParam String shop,
            @RequestParam(defaultValue = "10") int limit) {

        logger.info("GET /api/analytics/chat/top-errors - shop: {}, limit: {}", shop, limit);

        try {
            if (!shopifyShopService.isShopActive(shop)) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Shop not found", "Shop is not registered or inactive"));
            }

            ShopifyShop shopConfig = shopifyShopService.getShop(shop);
            List<Object[]> topErrors = chatAnalyticsService.getTopErrors(shopConfig, limit);

            return ResponseEntity.ok(ApiResponse.success(topErrors));
        } catch (Exception e) {
            logger.error("Error fetching top errors for shop {}", shop, e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to fetch top errors", e.getMessage()));
        }
    }

    /**
     * GET /api/analytics/chat/feedback-summary?shop={shop}
     * Get user feedback summary (positive/negative/neutral counts)
     *
     * @param shop Shop domain
     * @return Feedback summary
     */
    @GetMapping("/feedback-summary")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getFeedbackSummary(
            @RequestParam String shop) {

        logger.info("GET /api/analytics/chat/feedback-summary - shop: {}", shop);

        try {
            if (!shopifyShopService.isShopActive(shop)) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Shop not found", "Shop is not registered or inactive"));
            }

            ShopifyShop shopConfig = shopifyShopService.getShop(shop);
            Map<String, Long> feedbackSummary = chatAnalyticsService.getFeedbackSummary(shopConfig);

            return ResponseEntity.ok(ApiResponse.success(feedbackSummary));
        } catch (Exception e) {
            logger.error("Error fetching feedback summary for shop {}", shop, e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to fetch feedback summary", e.getMessage()));
        }
    }

    /**
     * GET /api/analytics/chat/tools-usage?shop={shop}
     * Get most used tools (e.g., search_products, delegate_to_agent)
     *
     * @param shop Shop domain
     * @return Tool usage statistics
     */
    @GetMapping("/tools-usage")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getToolsUsage(
            @RequestParam String shop) {

        logger.info("GET /api/analytics/chat/tools-usage - shop: {}", shop);

        try {
            if (!shopifyShopService.isShopActive(shop)) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Shop not found", "Shop is not registered or inactive"));
            }

            ShopifyShop shopConfig = shopifyShopService.getShop(shop);
            List<Map<String, Object>> toolsUsage = chatAnalyticsService.getMostUsedTools(shopConfig);

            return ResponseEntity.ok(ApiResponse.success(toolsUsage));
        } catch (Exception e) {
            logger.error("Error fetching tools usage for shop {}", shop, e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to fetch tools usage", e.getMessage()));
        }
    }

    /**
     * GET /api/analytics/chat/comprehensive?shop={shop}&days={days}
     * Get comprehensive analytics report
     *
     * @param shop Shop domain
     * @param days Number of days to analyze (default: 30)
     * @return Comprehensive analytics report
     */
    @GetMapping("/comprehensive")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getComprehensiveReport(
            @RequestParam String shop,
            @RequestParam(defaultValue = "30") int days) {

        logger.info("GET /api/analytics/chat/comprehensive - shop: {}, days: {}", shop, days);

        try {
            if (!shopifyShopService.isShopActive(shop)) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Shop not found", "Shop is not registered or inactive"));
            }

            ShopifyShop shopConfig = shopifyShopService.getShop(shop);
            Map<String, Object> report = chatAnalyticsService.getComprehensiveReport(shopConfig, days);

            return ResponseEntity.ok(ApiResponse.success(report));
        } catch (Exception e) {
            logger.error("Error generating comprehensive report for shop {}", shop, e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to generate report", e.getMessage()));
        }
    }

    /**
     * PUT /api/analytics/chat/{analyticsId}/feedback
     * Update user feedback for an interaction
     *
     * @param analyticsId Analytics record ID
     * @param feedbackRequest Request body with feedback value
     * @return Updated analytics record
     */
    @PutMapping("/{analyticsId}/feedback")
    public ResponseEntity<ApiResponse<String>> updateFeedback(
            @PathVariable Long analyticsId,
            @RequestBody Map<String, String> feedbackRequest) {

        logger.info("PUT /api/analytics/chat/{}/feedback", analyticsId);

        try {
            String feedback = feedbackRequest.get("feedback");

            if (feedback == null || feedback.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid feedback", "Feedback value is required"));
            }

            // Validate feedback value
            if (!feedback.equals("positive") && !feedback.equals("negative") && !feedback.equals("neutral")) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid feedback",
                        "Feedback must be 'positive', 'negative', or 'neutral'"));
            }

            chatAnalyticsService.updateUserFeedback(analyticsId, feedback);

            return ResponseEntity.ok(ApiResponse.success("Feedback updated successfully"));
        } catch (Exception e) {
            logger.error("Error updating feedback for analytics {}", analyticsId, e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to update feedback", e.getMessage()));
        }
    }
}
