package com.shopify.api.repository;

import com.shopify.api.model.AdminActivityLog;
import com.shopify.api.model.ShopifyShop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Repository interface for AdminActivityLog entity.
 * Provides database access for admin activity audit trail.
 */
@Repository
public interface AdminActivityLogRepository extends JpaRepository<AdminActivityLog, Long> {

    /**
     * Find all activity logs for a specific shop
     */
    Page<AdminActivityLog> findByShopOrderByCreatedAtDesc(ShopifyShop shop, Pageable pageable);

    /**
     * Find activity logs by shop and action category
     */
    Page<AdminActivityLog> findByShopAndActionCategoryOrderByCreatedAtDesc(
            ShopifyShop shop,
            String actionCategory,
            Pageable pageable
    );

    /**
     * Find activity logs by shop and action type
     */
    Page<AdminActivityLog> findByShopAndActionTypeOrderByCreatedAtDesc(
            ShopifyShop shop,
            String actionType,
            Pageable pageable
    );

    /**
     * Find activity logs by user email
     */
    List<AdminActivityLog> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    /**
     * Find failed actions for a shop
     */
    List<AdminActivityLog> findByShopAndSuccessFalseOrderByCreatedAtDesc(ShopifyShop shop);

    /**
     * Find recent activity logs (last N days)
     */
    @Query("SELECT a FROM AdminActivityLog a WHERE a.shop = :shop " +
           "AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<AdminActivityLog> findRecentActivity(
            @Param("shop") ShopifyShop shop,
            @Param("since") ZonedDateTime since
    );

    /**
     * Find activity logs by resource type and ID
     */
    List<AdminActivityLog> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(
            String resourceType,
            String resourceId
    );

    /**
     * Count actions by category for a shop
     */
    @Query("SELECT a.actionCategory, COUNT(a) FROM AdminActivityLog a " +
           "WHERE a.shop = :shop AND a.createdAt >= :since " +
           "GROUP BY a.actionCategory")
    List<Object[]> countActionsByCategory(
            @Param("shop") ShopifyShop shop,
            @Param("since") ZonedDateTime since
    );

    /**
     * Count failed actions for a shop
     */
    @Query("SELECT COUNT(a) FROM AdminActivityLog a " +
           "WHERE a.shop = :shop AND a.success = false AND a.createdAt >= :since")
    Long countFailedActions(
            @Param("shop") ShopifyShop shop,
            @Param("since") ZonedDateTime since
    );

    /**
     * Get average action duration by category
     */
    @Query("SELECT a.actionCategory, AVG(a.durationMs) FROM AdminActivityLog a " +
           "WHERE a.shop = :shop AND a.durationMs IS NOT NULL AND a.createdAt >= :since " +
           "GROUP BY a.actionCategory")
    List<Object[]> getAverageDurationByCategory(
            @Param("shop") ShopifyShop shop,
            @Param("since") ZonedDateTime since
    );

    /**
     * Find logs that need anonymization (older than retention period)
     */
    @Query("SELECT a FROM AdminActivityLog a " +
           "WHERE a.anonymized = false AND a.createdAt < :cutoffDate")
    List<AdminActivityLog> findLogsToAnonymize(@Param("cutoffDate") ZonedDateTime cutoffDate);

    /**
     * Search activity logs by change summary
     */
    @Query("SELECT a FROM AdminActivityLog a WHERE a.shop = :shop " +
           "AND LOWER(a.changeSummary) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "ORDER BY a.createdAt DESC")
    List<AdminActivityLog> searchByChangeSummary(
            @Param("shop") ShopifyShop shop,
            @Param("searchTerm") String searchTerm
    );

    /**
     * Find most active users for a shop
     */
    @Query("SELECT a.userEmail, COUNT(a) FROM AdminActivityLog a " +
           "WHERE a.shop = :shop AND a.createdAt >= :since " +
           "GROUP BY a.userEmail ORDER BY COUNT(a) DESC")
    List<Object[]> findMostActiveUsers(
            @Param("shop") ShopifyShop shop,
            @Param("since") ZonedDateTime since,
            Pageable pageable
    );

    /**
     * Count total actions for a shop in date range
     */
    Long countByShopAndCreatedAtBetween(
            ShopifyShop shop,
            ZonedDateTime startDate,
            ZonedDateTime endDate
    );
}
