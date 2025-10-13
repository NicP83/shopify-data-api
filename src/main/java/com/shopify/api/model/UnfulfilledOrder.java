package com.shopify.api.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Shopify order that has not been fulfilled in CRS ERP
 * Used for tracking orders that need to be processed
 */
public class UnfulfilledOrder {
    private String orderId;
    private String orderName;
    private String customerEmail;
    private String customerName;
    private String customerPhone;
    private BigDecimal totalPrice;
    private String currencyCode;
    private Integer itemCount;
    private String shippingAddress;
    private List<OrderLineItem> lineItems;
    private LocalDateTime createdAt;
    private String displayFulfillmentStatus;
    private String note;
    private BigDecimal subtotalPrice;  // Subtotal before discounts
    private BigDecimal totalDiscounts;  // Total discount amount
    private String discountCodes;  // Discount codes applied to order

    // Customer tier fields (calculated from CRS ERP data)
    private BigDecimal customerYearlySpend;  // Total CRS spend in last 365 days
    private Integer customerOrderCount;       // Total CRS orders in last 365 days
    private Integer customerRecentOrders;     // CRS orders in last 30 days
    private String customerTier;              // GOLD, SILVER, BRONZE, REPEAT, NEW

    public UnfulfilledOrder() {
        this.lineItems = new ArrayList<>();
    }

    // Getters and Setters
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderName() {
        return orderName;
    }

    public void setOrderName(String orderName) {
        this.orderName = orderName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public List<OrderLineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<OrderLineItem> lineItems) {
        this.lineItems = lineItems;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getDisplayFulfillmentStatus() {
        return displayFulfillmentStatus;
    }

    public void setDisplayFulfillmentStatus(String displayFulfillmentStatus) {
        this.displayFulfillmentStatus = displayFulfillmentStatus;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public BigDecimal getSubtotalPrice() {
        return subtotalPrice;
    }

    public void setSubtotalPrice(BigDecimal subtotalPrice) {
        this.subtotalPrice = subtotalPrice;
    }

    public BigDecimal getTotalDiscounts() {
        return totalDiscounts;
    }

    public void setTotalDiscounts(BigDecimal totalDiscounts) {
        this.totalDiscounts = totalDiscounts;
    }

    public String getDiscountCodes() {
        return discountCodes;
    }

    public void setDiscountCodes(String discountCodes) {
        this.discountCodes = discountCodes;
    }

    public BigDecimal getCustomerYearlySpend() {
        return customerYearlySpend;
    }

    public void setCustomerYearlySpend(BigDecimal customerYearlySpend) {
        this.customerYearlySpend = customerYearlySpend;
    }

    public Integer getCustomerOrderCount() {
        return customerOrderCount;
    }

    public void setCustomerOrderCount(Integer customerOrderCount) {
        this.customerOrderCount = customerOrderCount;
    }

    public Integer getCustomerRecentOrders() {
        return customerRecentOrders;
    }

    public void setCustomerRecentOrders(Integer customerRecentOrders) {
        this.customerRecentOrders = customerRecentOrders;
    }

    public String getCustomerTier() {
        return customerTier;
    }

    public void setCustomerTier(String customerTier) {
        this.customerTier = customerTier;
    }

    @Override
    public String toString() {
        return "UnfulfilledOrder{" +
                "orderId='" + orderId + '\'' +
                ", orderName='" + orderName + '\'' +
                ", customerName='" + customerName + '\'' +
                ", totalPrice=" + totalPrice +
                ", itemCount=" + itemCount +
                ", createdAt=" + createdAt +
                '}';
    }
}
