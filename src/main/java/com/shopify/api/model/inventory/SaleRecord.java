package com.shopify.api.model.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO representing a sale record from ERP
 * Used for calculating sales velocity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleRecord {

    private String sku;
    private LocalDate date;
    private Integer quantity;
    private BigDecimal amount; // Total sale amount
    private String orderId; // Reference to Shopify order

    // Optional metadata
    private String channel; // e.g., "ONLINE", "POS", "WHOLESALE"
    private String customerType; // e.g., "RETAIL", "WHOLESALE"

    /**
     * Check if this is a recent sale (within last N days)
     */
    public boolean isRecent(int days) {
        return date.isAfter(LocalDate.now().minusDays(days));
    }

    /**
     * Get days ago this sale occurred
     */
    public long getDaysAgo() {
        return LocalDate.now().toEpochDay() - date.toEpochDay();
    }
}
