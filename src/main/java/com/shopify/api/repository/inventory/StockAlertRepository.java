package com.shopify.api.repository.inventory;

import com.shopify.api.model.inventory.AlertLevel;
import com.shopify.api.model.inventory.StockAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for StockAlert entities
 */
@Repository
public interface StockAlertRepository extends JpaRepository<StockAlert, Long> {

    /**
     * Find all unresolved alerts
     */
    List<StockAlert> findByResolvedFalseOrderByCreatedAtDesc();

    /**
     * Find unresolved alerts by level
     */
    List<StockAlert> findByResolvedFalseAndAlertLevelOrderByCreatedAtDesc(AlertLevel level);

    /**
     * Find alerts by SKU
     */
    List<StockAlert> findBySkuOrderByCreatedAtDesc(String sku);

    /**
     * Find most recent unresolved alert for SKU
     */
    Optional<StockAlert> findTopBySkuAndResolvedFalseOrderByCreatedAtDesc(String sku);

    /**
     * Find critical unresolved alerts
     */
    @Query("SELECT a FROM StockAlert a WHERE a.resolved = FALSE AND a.alertLevel = 'CRITICAL' ORDER BY a.daysUntilStockout ASC NULLS LAST, a.createdAt DESC")
    List<StockAlert> findCriticalUnresolvedAlerts();

    /**
     * Find alerts with stockout date before given date (imminent stockouts)
     */
    List<StockAlert> findByResolvedFalseAndEstimatedStockoutDateBeforeOrderByEstimatedStockoutDateAsc(LocalDate date);

    /**
     * Find alerts created after a specific date
     */
    List<StockAlert> findByCreatedAtAfter(LocalDateTime date);

    /**
     * Count unresolved alerts
     */
    long countByResolvedFalse();

    /**
     * Count unresolved alerts by level
     */
    long countByResolvedFalseAndAlertLevel(AlertLevel level);

    /**
     * Find alerts linked to a recommendation
     */
    List<StockAlert> findByRecommendationId(Long recommendationId);

    /**
     * Resolve all alerts for a SKU
     *
     * @param sku The product SKU
     * @param resolvedAt Timestamp of resolution
     * @return Number of alerts resolved
     */
    @Modifying
    @Query("UPDATE StockAlert a SET a.resolved = TRUE, a.resolvedAt = :resolvedAt WHERE a.sku = :sku AND a.resolved = FALSE")
    int resolveAllForSku(@Param("sku") String sku, @Param("resolvedAt") LocalDateTime resolvedAt);

    /**
     * Automatically resolve old unresolved alerts
     * Alerts older than 30 days are considered stale
     *
     * @param cutoffDate Alerts created before this date will be resolved
     * @return Number of alerts resolved
     */
    @Modifying
    @Query("UPDATE StockAlert a SET a.resolved = TRUE, a.resolvedAt = CURRENT_TIMESTAMP WHERE a.resolved = FALSE AND a.createdAt < :cutoffDate")
    int autoResolveOldAlerts(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Delete resolved alerts older than retention period
     *
     * @param cutoffDate Delete alerts resolved before this date
     * @return Number of alerts deleted
     */
    @Modifying
    @Query("DELETE FROM StockAlert a WHERE a.resolved = TRUE AND a.resolvedAt < :cutoffDate")
    int deleteOldResolvedAlerts(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Check if unresolved alert exists for SKU
     */
    boolean existsBySkuAndResolvedFalse(String sku);
}
