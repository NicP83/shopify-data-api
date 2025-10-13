package com.shopify.api.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents sales data breakdown by all channels
 * Includes: Store 1 (Hobbyman), Store 2 (Hearns Hobbies), Online (Shopify), and Total
 */
public class SalesByChannelData {
    private String period;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    // Pure in-store sales (walk-in customers only)
    private ChannelSalesData hobbyman;
    private ChannelSalesData hearnsHobbies;

    // Online orders fulfilled in-store
    private ChannelSalesData hobbymanFulfillment;
    private ChannelSalesData hearnsFulfillment;

    // Online sales (Shopify)
    private ChannelSalesData shopify;

    // Online fulfillment details
    private Integer onlineFulfilledOrders;
    private BigDecimal onlineFulfilledRevenue;
    private Integer onlinePendingOrders;
    private BigDecimal onlinePendingRevenue;

    // Totals (no double-counting)
    private Integer totalOrders;
    private Integer totalItems;
    private BigDecimal totalRevenue;

    // Year-over-year comparison for total
    private PeriodComparison yearOverYearComparison;

    public SalesByChannelData() {
    }

    // Getters and Setters
    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public LocalDateTime getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDateTime periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDateTime getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDateTime periodEnd) {
        this.periodEnd = periodEnd;
    }

    public ChannelSalesData getHobbyman() {
        return hobbyman;
    }

    public void setHobbyman(ChannelSalesData hobbyman) {
        this.hobbyman = hobbyman;
    }

    public ChannelSalesData getHearnsHobbies() {
        return hearnsHobbies;
    }

    public void setHearnsHobbies(ChannelSalesData hearnsHobbies) {
        this.hearnsHobbies = hearnsHobbies;
    }

    public ChannelSalesData getShopify() {
        return shopify;
    }

    public void setShopify(ChannelSalesData shopify) {
        this.shopify = shopify;
    }

    public Integer getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(Integer totalOrders) {
        this.totalOrders = totalOrders;
    }

    public Integer getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(Integer totalItems) {
        this.totalItems = totalItems;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public PeriodComparison getYearOverYearComparison() {
        return yearOverYearComparison;
    }

    public void setYearOverYearComparison(PeriodComparison yearOverYearComparison) {
        this.yearOverYearComparison = yearOverYearComparison;
    }

    public ChannelSalesData getHobbymanFulfillment() {
        return hobbymanFulfillment;
    }

    public void setHobbymanFulfillment(ChannelSalesData hobbymanFulfillment) {
        this.hobbymanFulfillment = hobbymanFulfillment;
    }

    public ChannelSalesData getHearnsFulfillment() {
        return hearnsFulfillment;
    }

    public void setHearnsFulfillment(ChannelSalesData hearnsFulfillment) {
        this.hearnsFulfillment = hearnsFulfillment;
    }

    public Integer getOnlineFulfilledOrders() {
        return onlineFulfilledOrders;
    }

    public void setOnlineFulfilledOrders(Integer onlineFulfilledOrders) {
        this.onlineFulfilledOrders = onlineFulfilledOrders;
    }

    public BigDecimal getOnlineFulfilledRevenue() {
        return onlineFulfilledRevenue;
    }

    public void setOnlineFulfilledRevenue(BigDecimal onlineFulfilledRevenue) {
        this.onlineFulfilledRevenue = onlineFulfilledRevenue;
    }

    public Integer getOnlinePendingOrders() {
        return onlinePendingOrders;
    }

    public void setOnlinePendingOrders(Integer onlinePendingOrders) {
        this.onlinePendingOrders = onlinePendingOrders;
    }

    public BigDecimal getOnlinePendingRevenue() {
        return onlinePendingRevenue;
    }

    public void setOnlinePendingRevenue(BigDecimal onlinePendingRevenue) {
        this.onlinePendingRevenue = onlinePendingRevenue;
    }

    @Override
    public String toString() {
        return "SalesByChannelData{" +
                "period='" + period + '\'' +
                ", hobbyman=" + hobbyman +
                ", hearnsHobbies=" + hearnsHobbies +
                ", shopify=" + shopify +
                ", totalOrders=" + totalOrders +
                ", totalRevenue=" + totalRevenue +
                '}';
    }
}
