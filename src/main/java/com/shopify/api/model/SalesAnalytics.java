package com.shopify.api.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Model representing sales analytics for a specific time period
 */
public class SalesAnalytics {

    private String period;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private Integer orderCount;
    private BigDecimal totalSales;
    private BigDecimal averageSale;
    private BigDecimal totalFreight;
    private BigDecimal totalDiscounts;
    private String currencyCode;
    private PeriodComparison yearOverYearComparison;

    // Constructors
    public SalesAnalytics() {}

    public SalesAnalytics(String period, LocalDateTime periodStart, LocalDateTime periodEnd) {
        this.period = period;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.orderCount = 0;
        this.totalSales = BigDecimal.ZERO;
        this.averageSale = BigDecimal.ZERO;
        this.totalFreight = BigDecimal.ZERO;
        this.totalDiscounts = BigDecimal.ZERO;
        this.currencyCode = "USD";
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

    public Integer getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Integer orderCount) {
        this.orderCount = orderCount;
    }

    public BigDecimal getTotalSales() {
        return totalSales;
    }

    public void setTotalSales(BigDecimal totalSales) {
        this.totalSales = totalSales;
    }

    public BigDecimal getAverageSale() {
        return averageSale;
    }

    public void setAverageSale(BigDecimal averageSale) {
        this.averageSale = averageSale;
    }

    public BigDecimal getTotalFreight() {
        return totalFreight;
    }

    public void setTotalFreight(BigDecimal totalFreight) {
        this.totalFreight = totalFreight;
    }

    public BigDecimal getTotalDiscounts() {
        return totalDiscounts;
    }

    public void setTotalDiscounts(BigDecimal totalDiscounts) {
        this.totalDiscounts = totalDiscounts;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public PeriodComparison getYearOverYearComparison() {
        return yearOverYearComparison;
    }

    public void setYearOverYearComparison(PeriodComparison yearOverYearComparison) {
        this.yearOverYearComparison = yearOverYearComparison;
    }

    /**
     * Calculate average sale from total sales and order count
     */
    public void calculateAverageSale() {
        if (orderCount != null && orderCount > 0 && totalSales != null) {
            this.averageSale = totalSales.divide(
                BigDecimal.valueOf(orderCount),
                2,
                BigDecimal.ROUND_HALF_UP
            );
        } else {
            this.averageSale = BigDecimal.ZERO;
        }
    }
}
