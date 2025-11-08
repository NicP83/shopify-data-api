package com.shopify.api.model.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Represents a single item in a purchase order
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    private String sku;
    private String productName;
    private String brand;
    private String category;
    private String supplier;

    private Integer quantity;
    private Integer targetDays;

    private BigDecimal dailyVelocity;
    private BigDecimal unitCost;
    private BigDecimal totalCost;

    private Integer currentStock;
    private Integer daysRemaining;

    private UrgencyLevel urgency;
    private String notes;

    /**
     * Calculate total cost from unit cost and quantity
     */
    public void calculateTotalCost() {
        if (unitCost != null && quantity != null) {
            this.totalCost = unitCost.multiply(BigDecimal.valueOf(quantity));
        }
    }

    /**
     * Determine urgency level based on days remaining
     */
    public void determineUrgency() {
        if (daysRemaining == null) {
            this.urgency = UrgencyLevel.MEDIUM;
            return;
        }

        if (daysRemaining <= 3) {
            this.urgency = UrgencyLevel.CRITICAL;
        } else if (daysRemaining <= 7) {
            this.urgency = UrgencyLevel.HIGH;
        } else if (daysRemaining <= 14) {
            this.urgency = UrgencyLevel.MEDIUM;
        } else {
            this.urgency = UrgencyLevel.LOW;
        }
    }
}
