package com.shopify.api.model.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a complete purchase order plan grouped by supplier
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPlan {

    private String planId;
    private String planName;

    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Builder.Default
    private Map<String, SupplierOrder> supplierOrders = new HashMap<>();

    private BigDecimal grandTotal;
    private Integer totalItems;
    private Integer totalUnits;

    private LocalDateTime createdAt;
    private String createdBy;

    private String status; // DRAFT, SUBMITTED, APPROVED, ORDERED

    private String notes;

    /**
     * Group items by supplier and calculate totals
     */
    public void groupBySupplier() {
        this.supplierOrders = new HashMap<>();

        for (OrderItem item : items) {
            String supplier = item.getSupplier();
            if (supplier == null) {
                supplier = "Unknown";
            }

            SupplierOrder supplierOrder = supplierOrders.computeIfAbsent(supplier, s ->
                    SupplierOrder.builder()
                            .supplierName(s)
                            .items(new ArrayList<>())
                            .build()
            );

            supplierOrder.addItem(item);
        }

        // Calculate totals for each supplier
        supplierOrders.values().forEach(SupplierOrder::calculateTotals);

        // Calculate overall totals
        calculateTotals();
    }

    /**
     * Calculate overall plan totals
     */
    public void calculateTotals() {
        if (items == null || items.isEmpty()) {
            this.grandTotal = BigDecimal.ZERO;
            this.totalItems = 0;
            this.totalUnits = 0;
            return;
        }

        this.totalItems = items.size();
        this.totalUnits = items.stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();

        this.grandTotal = items.stream()
                .map(OrderItem::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Add item to the plan
     */
    public void addItem(OrderItem item) {
        if (this.items == null) {
            this.items = new ArrayList<>();
        }
        this.items.add(item);
    }
}
