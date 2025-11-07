package com.shopify.api.model.inventory;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing calculated sales velocity for a product
 * Tracks how fast a product sells over different time periods
 */
@Entity
@Table(name = "sales_velocity")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesVelocity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String sku;

    @Column(name = "product_id", length = 255)
    private String productId; // Shopify product ID for cross-reference

    // Velocity metrics - units sold per time period
    @Column(name = "daily_average", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyAverage;

    @Column(name = "weekly_average", nullable = false, precision = 10, scale = 2)
    private BigDecimal weeklyAverage;

    @Column(name = "monthly_average", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyAverage;

    // Trend analysis
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TrendDirection trend;

    @Column(name = "trend_percentage", precision = 5, scale = 2)
    private BigDecimal trendPercentage; // Positive or negative percentage change

    // Calculation metadata
    @Column(name = "last_calculated", nullable = false)
    private LocalDateTime lastCalculated;

    @Column(name = "calculation_period_days", nullable = false)
    private Integer calculationPeriodDays; // e.g., 30, 60, 90

    @Column(name = "data_sample_size")
    private Integer dataSampleSize; // Number of sales transactions analyzed

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (lastCalculated == null) {
            lastCalculated = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        lastCalculated = LocalDateTime.now();
    }

    /**
     * Check if velocity data is stale and needs recalculation
     * @param maxAgeHours Maximum age in hours before considered stale
     * @return true if data is stale
     */
    public boolean isStale(int maxAgeHours) {
        return lastCalculated.plusHours(maxAgeHours).isBefore(LocalDateTime.now());
    }

    /**
     * Get trend direction as a user-friendly string
     */
    public String getTrendDescription() {
        if (trendPercentage == null) {
            return trend.name();
        }
        return String.format("%s (%+.1f%%)", trend.name(), trendPercentage);
    }
}
