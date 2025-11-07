package com.shopify.api.model.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO representing inventory data from the ERP system via MCP
 * This is not a database entity - data comes from MCP calls
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ERPInventoryData {

    private String sku;
    private Integer quantity;
    private String location;
    private String warehouseLocation;

    // Supplier information
    private String supplierName;
    private String supplierCode;
    private Integer leadTimeDays;

    // Costing
    private BigDecimal costPerUnit;
    private String currency;

    // Reorder settings from ERP
    private Integer reorderQuantity;
    private Integer minimumStockLevel;

    // Metadata
    private LocalDateTime lastUpdated;

    // Calculated fields
    private Integer reserved; // Reserved/allocated inventory
    private Integer available; // Available = quantity - reserved

    /**
     * Check if inventory is tracked in ERP
     */
    public boolean isTracked() {
        return quantity != null && quantity >= 0;
    }

    /**
     * Get effective quantity (available if present, otherwise quantity)
     */
    public Integer getEffectiveQuantity() {
        return available != null ? available : quantity;
    }
}
