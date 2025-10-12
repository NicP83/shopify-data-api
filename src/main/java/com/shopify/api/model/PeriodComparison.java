package com.shopify.api.model;

import java.math.BigDecimal;

/**
 * Model representing year-over-year comparison data
 */
public class PeriodComparison {

    private BigDecimal currentValue;
    private BigDecimal previousValue;
    private BigDecimal percentageChange;
    private String trend; // "up", "down", "neutral"

    // Constructors
    public PeriodComparison() {}

    public PeriodComparison(BigDecimal currentValue, BigDecimal previousValue) {
        this.currentValue = currentValue;
        this.previousValue = previousValue;
        calculatePercentageChange();
    }

    // Getters and Setters
    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
        calculatePercentageChange();
    }

    public BigDecimal getPreviousValue() {
        return previousValue;
    }

    public void setPreviousValue(BigDecimal previousValue) {
        this.previousValue = previousValue;
        calculatePercentageChange();
    }

    public BigDecimal getPercentageChange() {
        return percentageChange;
    }

    public void setPercentageChange(BigDecimal percentageChange) {
        this.percentageChange = percentageChange;
    }

    public String getTrend() {
        return trend;
    }

    public void setTrend(String trend) {
        this.trend = trend;
    }

    /**
     * Calculate percentage change and trend
     * Formula: ((current - previous) / previous) * 100
     */
    public void calculatePercentageChange() {
        if (previousValue == null || previousValue.compareTo(BigDecimal.ZERO) == 0) {
            if (currentValue != null && currentValue.compareTo(BigDecimal.ZERO) > 0) {
                this.percentageChange = BigDecimal.valueOf(100.0);
                this.trend = "up";
            } else {
                this.percentageChange = BigDecimal.ZERO;
                this.trend = "neutral";
            }
            return;
        }

        if (currentValue == null) {
            this.percentageChange = BigDecimal.valueOf(-100.0);
            this.trend = "down";
            return;
        }

        BigDecimal difference = currentValue.subtract(previousValue);
        this.percentageChange = difference
            .divide(previousValue, 4, BigDecimal.ROUND_HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, BigDecimal.ROUND_HALF_UP);

        // Determine trend
        if (percentageChange.compareTo(BigDecimal.ZERO) > 0) {
            this.trend = "up";
        } else if (percentageChange.compareTo(BigDecimal.ZERO) < 0) {
            this.trend = "down";
        } else {
            this.trend = "neutral";
        }
    }
}
