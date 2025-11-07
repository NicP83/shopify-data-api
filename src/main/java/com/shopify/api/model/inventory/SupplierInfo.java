package com.shopify.api.model.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO representing supplier information from ERP
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierInfo {

    private String supplierName;
    private String supplierCode;
    private String contactEmail;
    private String contactPhone;

    // Lead time information
    private Integer leadTimeDays;
    private Double averageLeadTimeDays; // Historical average

    // Ordering constraints
    private Integer minimumOrderQuantity;
    private Integer orderMultiple; // e.g., must order in multiples of 6

    // Pricing
    private BigDecimal standardCost;
    private String currency;

    // Historical data
    private LocalDate lastOrderDate;
    private Integer lastOrderQuantity;
    private Double reliabilityScore; // 0.0 to 1.0 based on on-time delivery

    /**
     * Check if supplier has minimum order requirements
     */
    public boolean hasMinimumOrder() {
        return minimumOrderQuantity != null && minimumOrderQuantity > 1;
    }

    /**
     * Calculate actual order quantity accounting for constraints
     * @param desiredQuantity The desired order quantity
     * @return Adjusted quantity meeting supplier constraints
     */
    public Integer calculateActualOrderQuantity(Integer desiredQuantity) {
        if (desiredQuantity == null) {
            return minimumOrderQuantity != null ? minimumOrderQuantity : 1;
        }

        int quantity = desiredQuantity;

        // Apply minimum order quantity
        if (minimumOrderQuantity != null && quantity < minimumOrderQuantity) {
            quantity = minimumOrderQuantity;
        }

        // Apply order multiple constraint
        if (orderMultiple != null && orderMultiple > 1) {
            int remainder = quantity % orderMultiple;
            if (remainder != 0) {
                quantity = quantity + (orderMultiple - remainder);
            }
        }

        return quantity;
    }
}
