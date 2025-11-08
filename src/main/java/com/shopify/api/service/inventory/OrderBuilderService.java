package com.shopify.api.service.inventory;

import com.shopify.api.model.inventory.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for building and managing purchase orders
 * Helps users create orders from recommendations and analysis
 */
@Service
public class OrderBuilderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderBuilderService.class);

    /**
     * Create order plan from list of items
     */
    public OrderPlan createOrderPlan(List<OrderItem> items, String planName, String createdBy) {
        logger.info("Creating order plan: {} with {} items", planName, items.size());

        OrderPlan plan = OrderPlan.builder()
                .planId(UUID.randomUUID().toString())
                .planName(planName)
                .items(new ArrayList<>(items))
                .createdAt(LocalDateTime.now())
                .createdBy(createdBy)
                .status("DRAFT")
                .build();

        // Calculate costs for each item
        items.forEach(OrderItem::calculateTotalCost);

        // Group by supplier
        plan.groupBySupplier();

        logger.info("Order plan created: {} items, {} suppliers, total: ${}",
                plan.getTotalItems(),
                plan.getSupplierOrders().size(),
                plan.getGrandTotal());

        return plan;
    }

    /**
     * Add item to existing order plan
     */
    public OrderPlan addItem(OrderPlan plan, OrderItem item) {
        logger.info("Adding item to plan {}: SKU={}", plan.getPlanId(), item.getSku());

        item.calculateTotalCost();
        plan.addItem(item);
        plan.groupBySupplier();

        return plan;
    }

    /**
     * Remove item from order plan
     */
    public OrderPlan removeItem(OrderPlan plan, String sku) {
        logger.info("Removing item from plan {}: SKU={}", plan.getPlanId(), sku);

        plan.getItems().removeIf(item -> item.getSku().equals(sku));
        plan.groupBySupplier();

        return plan;
    }

    /**
     * Update item quantity in order plan
     */
    public OrderPlan updateQuantity(OrderPlan plan, String sku, int newQuantity) {
        logger.info("Updating quantity for SKU {} to {} in plan {}",
                sku, newQuantity, plan.getPlanId());

        plan.getItems().stream()
                .filter(item -> item.getSku().equals(sku))
                .findFirst()
                .ifPresent(item -> {
                    item.setQuantity(newQuantity);
                    item.calculateTotalCost();
                });

        plan.groupBySupplier();

        return plan;
    }

    /**
     * Apply minimum order quantity constraints
     */
    public OrderPlan applyMOQConstraints(OrderPlan plan, Map<String, Integer> moqBySupplier) {
        logger.info("Applying MOQ constraints to plan {}", plan.getPlanId());

        for (SupplierOrder supplierOrder : plan.getSupplierOrders().values()) {
            String supplier = supplierOrder.getSupplierName();
            Integer moq = moqBySupplier.get(supplier);

            if (moq != null && supplierOrder.getTotalUnits() < moq) {
                logger.warn("Supplier {} order ({} units) below MOQ ({} units)",
                        supplier, supplierOrder.getTotalUnits(), moq);

                // Proportionally increase quantities to meet MOQ
                int shortage = moq - supplierOrder.getTotalUnits();
                adjustQuantitiesProportionally(supplierOrder, shortage);
            }
        }

        plan.groupBySupplier();

        return plan;
    }

    /**
     * Calculate order plan from recommendations
     */
    public OrderPlan buildFromRecommendations(
            List<OrderRecommendation> recommendations,
            String planName,
            String createdBy) {

        logger.info("Building order plan from {} recommendations", recommendations.size());

        List<OrderItem> items = recommendations.stream()
                .map(this::recommendationToOrderItem)
                .collect(Collectors.toList());

        return createOrderPlan(items, planName, createdBy);
    }

    /**
     * Get order summary grouped by supplier
     */
    public Map<String, Object> getOrderSummary(OrderPlan plan) {
        Map<String, Object> summary = new HashMap<>();

        summary.put("planId", plan.getPlanId());
        summary.put("planName", plan.getPlanName());
        summary.put("totalItems", plan.getTotalItems());
        summary.put("totalUnits", plan.getTotalUnits());
        summary.put("grandTotal", plan.getGrandTotal());
        summary.put("status", plan.getStatus());
        summary.put("createdAt", plan.getCreatedAt());

        // Supplier breakdown
        List<Map<String, Object>> suppliers = new ArrayList<>();
        for (Map.Entry<String, SupplierOrder> entry : plan.getSupplierOrders().entrySet()) {
            SupplierOrder so = entry.getValue();

            Map<String, Object> supplierSummary = new HashMap<>();
            supplierSummary.put("supplier", so.getSupplierName());
            supplierSummary.put("itemCount", so.getItemCount());
            supplierSummary.put("totalUnits", so.getTotalUnits());
            supplierSummary.put("subtotal", so.getSubtotal());
            supplierSummary.put("meetsMinimum", so.getMeetsMinimum());

            suppliers.add(supplierSummary);
        }

        summary.put("suppliers", suppliers);

        return summary;
    }

    /**
     * Export order plan to CSV format
     */
    public String exportToCSV(OrderPlan plan) {
        StringBuilder csv = new StringBuilder();

        // Header
        csv.append("SKU,Product Name,Brand,Category,Supplier,Quantity,Unit Cost,Total Cost,Target Days,Urgency\n");

        // Items
        for (OrderItem item : plan.getItems()) {
            csv.append(String.format("%s,%s,%s,%s,%s,%d,%.2f,%.2f,%d,%s\n",
                    escapeCsv(item.getSku()),
                    escapeCsv(item.getProductName()),
                    escapeCsv(item.getBrand()),
                    escapeCsv(item.getCategory()),
                    escapeCsv(item.getSupplier()),
                    item.getQuantity(),
                    item.getUnitCost(),
                    item.getTotalCost(),
                    item.getTargetDays(),
                    item.getUrgency()
            ));
        }

        // Summary
        csv.append("\n");
        csv.append(String.format("Total Items:,%d\n", plan.getTotalItems()));
        csv.append(String.format("Total Units:,%d\n", plan.getTotalUnits()));
        csv.append(String.format("Grand Total:,%.2f\n", plan.getGrandTotal()));

        return csv.toString();
    }

    /**
     * Generate purchase order summary (for PDF/email)
     */
    public Map<String, Object> generatePOSummary(OrderPlan plan, String supplier) {
        Map<String, Object> po = new HashMap<>();

        SupplierOrder supplierOrder = plan.getSupplierOrders().get(supplier);
        if (supplierOrder == null) {
            throw new IllegalArgumentException("Supplier not found in plan: " + supplier);
        }

        po.put("poNumber", "PO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        po.put("poDate", LocalDateTime.now().toString());
        po.put("supplier", supplierOrder.getSupplierName());
        po.put("supplierContact", supplierOrder.getContactEmail());

        List<Map<String, Object>> lineItems = new ArrayList<>();
        int lineNumber = 1;

        for (OrderItem item : supplierOrder.getItems()) {
            Map<String, Object> line = new HashMap<>();
            line.put("line", lineNumber++);
            line.put("sku", item.getSku());
            line.put("description", item.getProductName());
            line.put("quantity", item.getQuantity());
            line.put("unitPrice", item.getUnitCost());
            line.put("total", item.getTotalCost());

            lineItems.add(line);
        }

        po.put("lineItems", lineItems);
        po.put("subtotal", supplierOrder.getSubtotal());
        po.put("shipping", supplierOrder.getShippingCost());
        po.put("total", supplierOrder.getGrandTotal());

        return po;
    }

    // ===== PRIVATE HELPERS =====

    /**
     * Convert OrderRecommendation to OrderItem
     */
    private OrderItem recommendationToOrderItem(OrderRecommendation rec) {
        return OrderItem.builder()
                .sku(rec.getSku())
                .productName(rec.getProductTitle())
                .brand("TBD") // Would come from ERP metadata
                .category("TBD") // Would come from ERP metadata
                .supplier(rec.getSupplierName())
                .quantity(rec.getRecommendedQuantity())
                .targetDays(30) // Default target days
                .dailyVelocity(BigDecimal.ZERO) // Would calculate from velocity
                .unitCost(rec.getCostPerUnit() != null ? rec.getCostPerUnit() : BigDecimal.ZERO)
                .currentStock(rec.getCurrentStock())
                .daysRemaining(rec.getDaysUntilStockout())
                .urgency(rec.getUrgency())
                .notes(rec.getAiReasoning())
                .build();
    }

    /**
     * Adjust quantities proportionally to meet MOQ
     */
    private void adjustQuantitiesProportionally(SupplierOrder supplierOrder, int shortage) {
        int itemCount = supplierOrder.getItems().size();
        int perItemIncrease = (int) Math.ceil((double) shortage / itemCount);

        for (OrderItem item : supplierOrder.getItems()) {
            item.setQuantity(item.getQuantity() + perItemIncrease);
            item.calculateTotalCost();
        }

        supplierOrder.calculateTotals();
    }

    /**
     * Escape CSV values
     */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
