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
