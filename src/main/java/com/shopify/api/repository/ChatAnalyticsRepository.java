package com.shopify.api.repository;

import com.shopify.api.model.ChatAnalytics;
import com.shopify.api.model.ShopifyShop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Repository for ChatAnalytics entities
 */
@Repository
public interface ChatAnalyticsRepository extends JpaRepository<ChatAnalytics, Long> {

    /**
     * Find analytics by shop
     */
    List<ChatAnalytics> findByShopOrderByCreatedAtDesc(ShopifyShop shop);

    /**
     * Find analytics by shop (pageable)
     */
    Page<ChatAnalytics> findByShop(ShopifyShop shop, Pageable pageable);

    /**
     * Find analytics by shop within date range
     */
    @Query("""
        SELECT ca FROM ChatAnalytics ca
        WHERE ca.shop = :shop
        AND ca.createdAt BETWEEN :startDate AND :endDate
        ORDER BY ca.createdAt DESC
        """)
    List<ChatAnalytics> findByShopAndDateRange(
        @Param("shop") ShopifyShop shop,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find analytics by session
     */
    List<ChatAnalytics> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * Count total interactions for a shop
     */
    long countByShop(ShopifyShop shop);

    /**
     * Count successful interactions for a shop
     */
    long countByShopAndWasSuccessfulTrue(ShopifyShop shop);

    /**
     * Calculate average response time for a shop
     */
    @Query("""
        SELECT AVG(ca.responseTimeMs)
        FROM ChatAnalytics ca
        WHERE ca.shop = :shop
        AND ca.responseTimeMs IS NOT NULL
        """)
    Double findAverageResponseTimeByShop(@Param("shop") ShopifyShop shop);

    /**
     * Calculate total API cost for a shop
     */
    @Query("""
        SELECT COALESCE(SUM(ca.apiCostUsd), 0)
        FROM ChatAnalytics ca
        WHERE ca.shop = :shop
        """)
    Double findTotalApiCostByShop(@Param("shop") ShopifyShop shop);

    /**
     * Get usage statistics by model
     */
    @Query("""
        SELECT ca.modelUsed as model, COUNT(ca) as count
        FROM ChatAnalytics ca
        WHERE ca.shop = :shop
        GROUP BY ca.modelUsed
        ORDER BY count DESC
        """)
    List<Map<String, Object>> findModelUsageByShop(@Param("shop") ShopifyShop shop);

    /**
     * Get daily interaction counts
     */
    @Query(value = """
        SELECT DATE(created_at) as date, COUNT(*) as count
        FROM chat_analytics
        WHERE shop_id = :shopId
        AND created_at >= :startDate
        GROUP BY DATE(created_at)
        ORDER BY date DESC
        """, nativeQuery = true)
    List<Map<String, Object>> findDailyInteractionCounts(
        @Param("shopId") Long shopId,
        @Param("startDate") LocalDateTime startDate
    );

    /**
     * Get top error messages
     */
    @Query("""
        SELECT ca.errorMessage, COUNT(ca) as count
        FROM ChatAnalytics ca
        WHERE ca.shop = :shop
        AND ca.wasSuccessful = false
        AND ca.errorMessage IS NOT NULL
        GROUP BY ca.errorMessage
        ORDER BY count DESC
        """)
    List<Object[]> findTopErrorMessagesByShop(
        @Param("shop") ShopifyShop shop,
        org.springframework.data.domain.Pageable pageable
    );

    /**
     * Get user feedback summary
     */
    @Query("""
        SELECT ca.userFeedback, COUNT(ca) as count
        FROM ChatAnalytics ca
        WHERE ca.shop = :shop
        AND ca.userFeedback IS NOT NULL
        GROUP BY ca.userFeedback
        """)
    List<Object[]> findFeedbackSummaryByShop(@Param("shop") ShopifyShop shop);

    /**
     * Get most used tools
     */
    @Query(value = """
        SELECT tool, COUNT(*) as count
        FROM chat_analytics, UNNEST(tools_called) AS tool
        WHERE shop_id = :shopId
        GROUP BY tool
        ORDER BY count DESC
        LIMIT 10
        """, nativeQuery = true)
    List<Map<String, Object>> findMostUsedTools(@Param("shopId") Long shopId);

    /**
     * Get recent analytics (last N records)
     */
    List<ChatAnalytics> findTop10ByShopOrderByCreatedAtDesc(ShopifyShop shop);

    /**
     * Find interactions with negative feedback
     */
    List<ChatAnalytics> findByShopAndUserFeedbackOrderByCreatedAtDesc(
        ShopifyShop shop,
        String userFeedback
    );

    /**
     * Delete old analytics (data retention)
     */
    @Query("""
        DELETE FROM ChatAnalytics ca
        WHERE ca.createdAt < :cutoffDate
        """)
    void deleteOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
}
