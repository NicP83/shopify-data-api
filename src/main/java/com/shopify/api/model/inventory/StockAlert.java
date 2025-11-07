package com.shopify.api.model.inventory;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing a stock level alert
 * Tracks products that are low on inventory and need attention
 */
@Entity
@Table(name = "stock_alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String sku;

    @Column(name = "product_title", length = 500)
    private String productTitle;

    // Stock levels
    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    @Column(name = "reorder_point", nullable = false)
    private Integer reorderPoint;

    // Alert details
    @Enumerated(EnumType.STRING)
    @Column(name = "alert_level", nullable = false, length = 50)
    private AlertLevel alertLevel;

    @Column(name = "days_until_stockout")
    private Integer daysUntilStockout;

    @Column(name = "estimated_stockout_date")
    private LocalDate estimatedStockoutDate;

    @Column(columnDefinition = "TEXT")
    private String message; // Human-readable alert message

    // Tracking
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean resolved = false;

    // Link to recommendation
    @Column(name = "recommendation_id")
    private Long recommendationId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (resolved == null) {
            resolved = false;
        }
    }

    /**
     * Resolve this alert
     */
    public void resolve() {
        this.resolved = true;
        this.resolvedAt = LocalDateTime.now();
    }

    /**
     * Check if this is a critical alert
     */
    public boolean isCritical() {
        return alertLevel == AlertLevel.CRITICAL;
    }

    /**
     * Generate a human-readable alert message
     */
    public static String generateMessage(AlertLevel level, Integer daysUntilStockout, String productTitle) {
        return switch (level) {
            case CRITICAL -> String.format("CRITICAL: %s will stock out in %d days! Immediate action required.",
                    productTitle, daysUntilStockout);
            case WARNING -> String.format("WARNING: %s is running low. Stock will last approximately %d days.",
                    productTitle, daysUntilStockout);
            case INFO -> String.format("INFO: %s inventory is below optimal levels. Consider reordering soon.",
                    productTitle);
        };
    }
}
