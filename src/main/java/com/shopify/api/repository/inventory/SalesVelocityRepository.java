package com.shopify.api.repository.inventory;

import com.shopify.api.model.inventory.SalesVelocity;
import com.shopify.api.model.inventory.TrendDirection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for SalesVelocity entities
 */
@Repository
public interface SalesVelocityRepository extends JpaRepository<SalesVelocity, Long> {

    /**
     * Find sales velocity by SKU
     */
    Optional<SalesVelocity> findBySku(String sku);

    /**
     * Find all velocities with a specific trend
     */
    List<SalesVelocity> findByTrend(TrendDirection trend);

    /**
     * Find velocities calculated after a specific date
     * Useful for finding recently updated velocities
     */
    List<SalesVelocity> findByLastCalculatedAfter(LocalDateTime cutoff);

    /**
     * Find stale velocities (not calculated recently)
     */
    List<SalesVelocity> findByLastCalculatedBefore(LocalDateTime cutoff);

    /**
     * Find velocities with high daily average (top sellers)
     *
     * @param threshold Minimum daily average
     * @return List of high-velocity products
     */
    @Query("SELECT sv FROM SalesVelocity sv WHERE sv.dailyAverage >= :threshold ORDER BY sv.dailyAverage DESC")
    List<SalesVelocity> findHighVelocityProducts(@Param("threshold") double threshold);

    /**
     * Find velocities with increasing trend
     */
    @Query("SELECT sv FROM SalesVelocity sv WHERE sv.trend = 'INCREASING' ORDER BY sv.trendPercentage DESC")
    List<SalesVelocity> findIncreasingTrends();

    /**
     * Find velocities with decreasing trend
     */
    @Query("SELECT sv FROM SalesVelocity sv WHERE sv.trend = 'DECREASING' ORDER BY sv.trendPercentage ASC")
    List<SalesVelocity> findDecreasingTrends();

    /**
     * Check if velocity exists for SKU
     */
    boolean existsBySku(String sku);

    /**
     * Delete velocity by SKU
     */
    void deleteBySku(String sku);

    /**
     * Count total velocities calculated
     */
    @Query("SELECT COUNT(sv) FROM SalesVelocity sv")
    long countTotal();

    /**
     * Get average daily velocity across all products
     */
    @Query("SELECT AVG(sv.dailyAverage) FROM SalesVelocity sv")
    Double getAverageDailyVelocity();
}
