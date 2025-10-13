package com.shopify.api.model;

import java.math.BigDecimal;

/**
 * Represents sales data for a specific sales channel
 */
public class ChannelSalesData {
    private String channelName;
    private Integer orderCount;
    private Integer itemCount;
    private BigDecimal revenue;

    public ChannelSalesData() {
    }

    public ChannelSalesData(String channelName, Integer orderCount, Integer itemCount, BigDecimal revenue) {
        this.channelName = channelName;
        this.orderCount = orderCount;
        this.itemCount = itemCount;
        this.revenue = revenue;
    }

    // Getters and Setters
    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public Integer getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Integer orderCount) {
        this.orderCount = orderCount;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public void setRevenue(BigDecimal revenue) {
        this.revenue = revenue;
    }

    @Override
    public String toString() {
        return "ChannelSalesData{" +
                "channelName='" + channelName + '\'' +
                ", orderCount=" + orderCount +
                ", itemCount=" + itemCount +
                ", revenue=" + revenue +
                '}';
    }
}
