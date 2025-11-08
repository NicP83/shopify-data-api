package com.shopify.api.model.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents items grouped by supplier for ordering
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierOrder {

    private String supplierName;
    private String supplierCode;
    private String contactEmail;
    private String contactPhone;

    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    private BigDecimal subtotal;
    private Integer itemCount;
    private Integer totalUnits;

    private BigDecimal minimumOrderValue;
    private Boolean meetsMinimum;

    private BigDecimal shippingCost;
    private BigDecimal grandTotal;

    /**
     * Calculate totals from items
     */
    public void calculateTotals() {
        if (items == null || items.isEmpty()) {
            this.subtotal = BigDecimal.ZERO;
            this.itemCount = 0;
            this.totalUnits = 0;
            return;
        }

        this.itemCount = items.size();
        this.totalUnits = items.stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();

        this.subtotal = items.stream()
                .map(OrderItem::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Check if meets minimum
        if (minimumOrderValue != null) {
            this.meetsMinimum = subtotal.compareTo(minimumOrderValue) >= 0;
        }

        // Calculate grand total
        this.grandTotal = subtotal;
        if (shippingCost != null) {
            this.grandTotal = grandTotal.add(shippingCost);
        }
    }

    /**
     * Add item to this supplier order
     */
    public void addItem(OrderItem item) {
        if (this.items == null) {
            this.items = new ArrayList<>();
        }
        this.items.add(item);
        calculateTotals();
    }
}
