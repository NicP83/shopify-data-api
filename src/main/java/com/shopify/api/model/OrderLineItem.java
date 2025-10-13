package com.shopify.api.model;

import java.math.BigDecimal;

/**
 * Represents a line item in a Shopify order
 * Used for fulfillment tracking
 */
public class OrderLineItem {
    private String lineItemId;
    private String title;
    private String sku;
    private String variantTitle;
    private Integer quantity;
    private BigDecimal price;

    public OrderLineItem() {
    }

    public OrderLineItem(String lineItemId, String title, String sku, String variantTitle, Integer quantity, BigDecimal price) {
        this.lineItemId = lineItemId;
        this.title = title;
        this.sku = sku;
        this.variantTitle = variantTitle;
        this.quantity = quantity;
        this.price = price;
    }

    // Getters and Setters
    public String getLineItemId() {
        return lineItemId;
    }

    public void setLineItemId(String lineItemId) {
        this.lineItemId = lineItemId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getVariantTitle() {
        return variantTitle;
    }

    public void setVariantTitle(String variantTitle) {
        this.variantTitle = variantTitle;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Override
    public String toString() {
        return "OrderLineItem{" +
                "lineItemId='" + lineItemId + '\'' +
                ", title='" + title + '\'' +
                ", sku='" + sku + '\'' +
                ", quantity=" + quantity +
                ", price=" + price +
                '}';
    }
}
