package com.shopify.api.repository.inventory;

import com.shopify.api.model.inventory.OrderRecommendation;
import com.shopify.api.model.inventory.RecommendationStatus;
import com.shopify.api.model.inventory.UrgencyLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for OrderRecommendation entities
 */
@Repository
public interface OrderRecommendationRepository extends JpaRepository<OrderRecommendation, Long> {

    /**
     * Find recommendations by status
     */
    List<OrderRecommendation> findByStatus(RecommendationStatus status);

    /**
     * Find recommendations by urgency level
     */
    List<OrderRecommendation> findByUrgency(UrgencyLevel urgency);

    /**
     * Find recommendations by status and urgency
     * Ordered by urgency and estimated stockout date
     */
    @Query("""
        SELECT r FROM OrderRecommendation r
        WHERE r.status = :status AND r.urgency = :urgency
        ORDER BY
            CASE r.urgency
                WHEN 'CRITICAL' THEN 1
                WHEN 'HIGH' THEN 2
                WHEN 'MEDIUM' THEN 3
                WHEN 'LOW' THEN 4
            END,
            r.estimatedStockoutDate ASC NULLS LAST
        """)
    List<OrderRecommendation> findByStatusAndUrgency(
            @Param("status") RecommendationStatus status,
            @Param("urgency") UrgencyLevel urgency
    );

    /**
     * Find most recent recommendation for a SKU
     */
    Optional<OrderRecommendation> findTopBySkuOrderByCreatedAtDesc(String sku);

    /**
     * Find all recommendations for a SKU
     */
    List<OrderRecommendation> findBySkuOrderByCreatedAtDesc(String sku);

    /**
     * Find critical recommendations (CRITICAL urgency, PENDING status)
     */
    @Query("SELECT r FROM OrderRecommendation r WHERE r.urgency = 'CRITICAL' AND r.status = 'PENDING' ORDER BY r.estimatedStockoutDate ASC")
    List<OrderRecommendation> findCriticalPendingRecommendations();

    /**
     * Find recommendations with stockout date before given date
     */
    List<OrderRecommendation> findByEstimatedStockoutDateBeforeAndStatus(
            LocalDate date,
            RecommendationStatus status
    );

    /**
     * Find recommendations created after a specific date
     */
    List<OrderRecommendation> findByCreatedAtAfter(LocalDateTime date);

    /**
     * Find recommendations with high confidence (>= threshold)
     */
    @Query("SELECT r FROM OrderRecommendation r WHERE r.confidenceScore >= :threshold AND r.status = :status")
    List<OrderRecommendation> findHighConfidenceRecommendations(
            @Param("threshold") BigDecimal threshold,
            @Param("status") RecommendationStatus status
    );

    /**
     * Count pending recommendations
     */
    long countByStatus(RecommendationStatus status);

    /**
     * Count critical recommendations
     */
    long countByUrgency(UrgencyLevel urgency);

    /**
     * Get total value of pending recommendations
     */
    @Query("SELECT COALESCE(SUM(r.totalCost), 0) FROM OrderRecommendation r WHERE r.status = 'PENDING'")
    BigDecimal getTotalPendingOrderValue();

    /**
     * Find recommendations by velocity ID
     */
    List<OrderRecommendation> findByVelocityId(Long velocityId);

    /**
     * Delete old dismissed recommendations
     *
     * @param cutoffDate Delete recommendations dismissed before this date
     * @return Number of deleted records
     */
    @Query("DELETE FROM OrderRecommendation r WHERE r.status = 'DISMISSED' AND r.createdAt < :cutoffDate")
    int deleteOldDismissedRecommendations(@Param("cutoffDate") LocalDateTime cutoffDate);
}
