package com.shopify.api.model.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO representing the complete analysis result for a product
 * Combines data from multiple sources with AI-generated insights
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAnalysisResult {

    // Product identification
    private String sku;
    private String productTitle;
    private String productId; // Shopify product ID

    // Current stock levels
    private Integer shopifyStock; // From Shopify
    private Integer erpStock; // From ERP
    private Integer totalAvailableStock; // Combined or primary source

    // Sales velocity
    private SalesVelocity velocity;

    // Supplier information
    private String supplierName;
    private Integer leadTimeDays;
    private BigDecimal costPerUnit;

    // Calculated metrics
    private Integer reorderPoint;
    private Integer safetyStockLevel;
    private Integer recommendedOrderQuantity;
    private Integer daysUntilStockout;
    private LocalDate estimatedStockoutDate;

    // AI insights
    private String aiAnalysis; // Claude's comprehensive analysis
    private String recommendations; // Specific action items
    private UrgencyLevel urgencyLevel;
    private BigDecimal confidenceScore; // 0.0 to 1.0

    // Analysis metadata
    private LocalDateTime analyzedAt;
    private String analysisVersion; // Version of analysis algorithm used
    private Integer calculationPeriodDays; // Days of history used

    /**
     * Check if this product needs immediate attention
     */
    public boolean needsImmediateAction() {
        return urgencyLevel == UrgencyLevel.CRITICAL ||
                (urgencyLevel == UrgencyLevel.HIGH && daysUntilStockout != null && daysUntilStockout <= 3);
    }

    /**
     * Check if stock is below reorder point
     */
    public boolean isBelowReorderPoint() {
        return totalAvailableStock != null && reorderPoint != null &&
                totalAvailableStock < reorderPoint;
    }

    /**
     * Get stock health status
     */
    public String getStockHealthStatus() {
        if (totalAvailableStock == null || reorderPoint == null) {
            return "UNKNOWN";
        }
        if (totalAvailableStock == 0) {
            return "OUT_OF_STOCK";
        }
        if (daysUntilStockout != null && daysUntilStockout <= 3) {
            return "CRITICAL";
        }
        if (totalAvailableStock < reorderPoint) {
            return "LOW";
        }
        if (totalAvailableStock < (reorderPoint * 1.5)) {
            return "FAIR";
        }
        return "HEALTHY";
    }

    /**
     * Calculate estimated total cost for recommended order
     */
    public BigDecimal getRecommendedOrderCost() {
        if (recommendedOrderQuantity == null || costPerUnit == null) {
            return null;
        }
        return costPerUnit.multiply(BigDecimal.valueOf(recommendedOrderQuantity));
    }
}
