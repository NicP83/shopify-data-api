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
    private BigDecimal discountedTotalPrice;  // Price after discounts
    private String discountAllocations;  // Description of discounts applied
    private Boolean onSale;  // Whether product was on sale in CRS
    private BigDecimal crsRegularPrice;  // Regular price from CRS
    private BigDecimal crsSalePrice;  // Sale price from CRS if on sale

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

    public BigDecimal getDiscountedTotalPrice() {
        return discountedTotalPrice;
    }

    public void setDiscountedTotalPrice(BigDecimal discountedTotalPrice) {
        this.discountedTotalPrice = discountedTotalPrice;
    }

    public String getDiscountAllocations() {
        return discountAllocations;
    }

    public void setDiscountAllocations(String discountAllocations) {
        this.discountAllocations = discountAllocations;
    }

    public Boolean getOnSale() {
        return onSale;
    }

    public void setOnSale(Boolean onSale) {
        this.onSale = onSale;
    }

    public BigDecimal getCrsRegularPrice() {
        return crsRegularPrice;
    }

    public void setCrsRegularPrice(BigDecimal crsRegularPrice) {
        this.crsRegularPrice = crsRegularPrice;
    }

    public BigDecimal getCrsSalePrice() {
        return crsSalePrice;
    }

    public void setCrsSalePrice(BigDecimal crsSalePrice) {
        this.crsSalePrice = crsSalePrice;
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
