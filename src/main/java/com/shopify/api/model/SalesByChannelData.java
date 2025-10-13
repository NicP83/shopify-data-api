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

    // Individual channels
    private ChannelSalesData hobbyman;
    private ChannelSalesData hearnsHobbies;
    private ChannelSalesData shopify;

    // Totals
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
