package com.shopify.api.service;

import com.shopify.api.model.ChatAnalytics;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.model.SystemPrompt;
import com.shopify.api.repository.ChatAnalyticsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for tracking and analyzing chat interactions
 */
@Service
public class ChatAnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(ChatAnalyticsService.class);

    private final ChatAnalyticsRepository chatAnalyticsRepository;

    @Autowired
    public ChatAnalyticsService(ChatAnalyticsRepository chatAnalyticsRepository) {
        this.chatAnalyticsRepository = chatAnalyticsRepository;
    }

    /**
     * Track a chat interaction
     */
    @Transactional
    public ChatAnalytics trackInteraction(ChatAnalytics analytics) {
        logger.debug("Tracking chat interaction for shop: {}",
            analytics.getShop() != null ? analytics.getShop().getShopDomain() : "null");

        return chatAnalyticsRepository.save(analytics);
    }

    /**
     * Create analytics record builder
     */
    public ChatAnalytics.ChatAnalyticsBuilder createAnalyticsBuilder(
            ShopifyShop shop,
            String sessionId,
            String userMessage) {

        return ChatAnalytics.builder()
            .shop(shop)
            .sessionId(sessionId)
            .userMessage(userMessage)
            .wasSuccessful(true);
    }

    /**
     * Get analytics summary for a shop
     */
    public Map<String, Object> getShopAnalyticsSummary(ShopifyShop shop) {
        logger.info("Generating analytics summary for shop: {}", shop.getShopDomain());

        Map<String, Object> summary = new HashMap<>();

        // Total interactions
        long totalInteractions = chatAnalyticsRepository.countByShop(shop);
        summary.put("totalInteractions", totalInteractions);

        // Successful interactions
        long successfulInteractions = chatAnalyticsRepository.countByShopAndWasSuccessfulTrue(shop);
        summary.put("successfulInteractions", successfulInteractions);

        // Success rate
        double successRate = totalInteractions > 0
            ? (successfulInteractions * 100.0 / totalInteractions)
            : 100.0;
        summary.put("successRate", String.format("%.2f%%", successRate));

        // Average response time
        Double avgResponseTime = chatAnalyticsRepository.findAverageResponseTimeByShop(shop);
        summary.put("averageResponseTimeMs", avgResponseTime != null ? avgResponseTime.intValue() : 0);

        // Total API cost
        Double totalCost = chatAnalyticsRepository.findTotalApiCostByShop(shop);
        summary.put("totalApiCost", String.format("$%.4f", totalCost != null ? totalCost : 0.0));

        // Model usage
        List<Map<String, Object>> modelUsage = chatAnalyticsRepository.findModelUsageByShop(shop);
        summary.put("modelUsage", modelUsage);

        // Recent interactions
        List<ChatAnalytics> recentInteractions = chatAnalyticsRepository
            .findTop10ByShopOrderByCreatedAtDesc(shop);
        summary.put("recentInteractionsCount", recentInteractions.size());

        return summary;
    }

    /**
     * Get daily interaction statistics
     */
    public List<Map<String, Object>> getDailyStats(ShopifyShop shop, int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        return chatAnalyticsRepository.findDailyInteractionCounts(shop.getId(), startDate);
    }

    /**
     * Get analytics for a date range
     */
    public List<ChatAnalytics> getAnalyticsByDateRange(
            ShopifyShop shop,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        return chatAnalyticsRepository.findByShopAndDateRange(shop, startDate, endDate);
    }

    /**
     * Get session history
     */
    public List<ChatAnalytics> getSessionHistory(String sessionId) {
        return chatAnalyticsRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * Get top error messages
     */
    public List<Object[]> getTopErrors(ShopifyShop shop, int limit) {
        return chatAnalyticsRepository.findTopErrorMessagesByShop(
            shop,
            PageRequest.of(0, limit)
        );
    }

    /**
     * Get user feedback summary
     */
    public Map<String, Long> getFeedbackSummary(ShopifyShop shop) {
        List<Object[]> results = chatAnalyticsRepository.findFeedbackSummaryByShop(shop);

        Map<String, Long> summary = new HashMap<>();
        for (Object[] result : results) {
            String feedback = (String) result[0];
            Long count = ((Number) result[1]).longValue();
            summary.put(feedback, count);
        }

        return summary;
    }

    /**
     * Get most used tools
     */
    public List<Map<String, Object>> getMostUsedTools(ShopifyShop shop) {
        return chatAnalyticsRepository.findMostUsedTools(shop.getId());
    }

    /**
     * Update user feedback for an interaction
     */
    @Transactional
    public void updateUserFeedback(Long analyticsId, String feedback) {
        ChatAnalytics analytics = chatAnalyticsRepository.findById(analyticsId)
            .orElseThrow(() -> new RuntimeException("Analytics record not found"));

        analytics.setUserFeedback(feedback);
        chatAnalyticsRepository.save(analytics);

        logger.info("Updated feedback for analytics {}: {}", analyticsId, feedback);
    }

    /**
     * Get interactions with negative feedback
     */
    public List<ChatAnalytics> getNegativeFeedbackInteractions(ShopifyShop shop) {
        return chatAnalyticsRepository.findByShopAndUserFeedbackOrderByCreatedAtDesc(
            shop,
            ChatAnalytics.Feedback.NEGATIVE
        );
    }

    /**
     * Clean up old analytics data (data retention policy)
     */
    @Transactional
    public void cleanupOldData(int retentionDays) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        logger.info("Cleaning up analytics data older than: {}", cutoffDate);

        chatAnalyticsRepository.deleteOlderThan(cutoffDate);
    }

    /**
     * Calculate estimated cost for a model and tokens
     */
    public BigDecimal calculateEstimatedCost(String model, int inputTokens, int outputTokens) {
        ChatAnalytics tempAnalytics = ChatAnalytics.builder()
            .modelUsed(model)
            .build();

        tempAnalytics.calculateApiCost(inputTokens, outputTokens);
        return tempAnalytics.getApiCostUsd();
    }

    /**
     * Get comprehensive analytics report
     */
    public Map<String, Object> getComprehensiveReport(ShopifyShop shop, int days) {
        logger.info("Generating comprehensive analytics report for shop: {} (last {} days)",
            shop.getShopDomain(), days);

        Map<String, Object> report = new HashMap<>();

        // Basic summary
        report.put("summary", getShopAnalyticsSummary(shop));

        // Daily stats
        report.put("dailyStats", getDailyStats(shop, days));

        // Top errors
        report.put("topErrors", getTopErrors(shop, 5));

        // Feedback summary
        report.put("feedbackSummary", getFeedbackSummary(shop));

        // Most used tools
        report.put("mostUsedTools", getMostUsedTools(shop));

        // Recent negative feedback
        List<ChatAnalytics> negativeFeedback = getNegativeFeedbackInteractions(shop);
        report.put("recentNegativeFeedbackCount", negativeFeedback.size());

        return report;
    }
}
