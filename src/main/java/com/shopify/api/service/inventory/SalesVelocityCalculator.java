package com.shopify.api.service.inventory;

import com.shopify.api.model.inventory.SaleRecord;
import com.shopify.api.model.inventory.SalesVelocity;
import com.shopify.api.model.inventory.TrendDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Calculator for sales velocity and trend analysis
 * Analyzes historical sales data to calculate how fast products sell
 */
@Component
public class SalesVelocityCalculator {

    private static final Logger logger = LoggerFactory.getLogger(SalesVelocityCalculator.class);

    // Trend detection threshold (percentage change)
    private static final double TREND_THRESHOLD = 15.0;

    /**
     * Calculate sales velocity from historical sales records
     *
     * @param salesRecords List of sale records
     * @param sku Product SKU
     * @return Calculated sales velocity
     */
    public SalesVelocity calculate(List<SaleRecord> salesRecords, String sku) {
        logger.info("Calculating sales velocity for SKU: {} with {} records", sku, salesRecords.size());

        if (salesRecords == null || salesRecords.isEmpty()) {
            logger.warn("No sales records for SKU: {}, returning zero velocity", sku);
            return createZeroVelocity(sku);
        }

        // Sort records by date
        List<SaleRecord> sortedRecords = salesRecords.stream()
                .sorted(Comparator.comparing(SaleRecord::getDate))
                .collect(Collectors.toList());

        // Calculate period in days
        int periodDays = calculatePeriodDays(sortedRecords);
        if (periodDays == 0) {
            periodDays = 1; // Avoid division by zero
        }

        // Calculate total units sold
        int totalUnitsSold = sortedRecords.stream()
                .mapToInt(SaleRecord::getQuantity)
                .sum();

        // Calculate daily average
        BigDecimal dailyAverage = BigDecimal.valueOf(totalUnitsSold)
                .divide(BigDecimal.valueOf(periodDays), 2, RoundingMode.HALF_UP);

        // Calculate weekly and monthly averages
        BigDecimal weeklyAverage = dailyAverage.multiply(BigDecimal.valueOf(7));
        BigDecimal monthlyAverage = dailyAverage.multiply(BigDecimal.valueOf(30));

        // Detect trend
        TrendAnalysis trendAnalysis = detectTrend(sortedRecords, periodDays);

        logger.info("Velocity calculated for SKU {}: Daily={}, Weekly={}, Monthly={}, Trend={}",
                sku, dailyAverage, weeklyAverage, monthlyAverage, trendAnalysis.direction);

        return SalesVelocity.builder()
                .sku(sku)
                .dailyAverage(dailyAverage)
                .weeklyAverage(weeklyAverage)
                .monthlyAverage(monthlyAverage)
                .trend(trendAnalysis.direction)
                .trendPercentage(trendAnalysis.percentage)
                .lastCalculated(LocalDateTime.now())
                .calculationPeriodDays(periodDays)
                .dataSampleSize(sortedRecords.size())
                .build();
    }

    /**
     * Calculate detailed velocity with multiple time windows
     * Uses weighted average to give more importance to recent sales
     *
     * @param salesRecords List of sale records
     * @param sku Product SKU
     * @return Calculated sales velocity with weighted analysis
     */
    public SalesVelocity calculateDetailed(List<SaleRecord> salesRecords, String sku) {
        logger.info("Calculating detailed velocity for SKU: {}", sku);

        if (salesRecords == null || salesRecords.isEmpty()) {
            return createZeroVelocity(sku);
        }

        // Sort records by date
        List<SaleRecord> sortedRecords = salesRecords.stream()
                .sorted(Comparator.comparing(SaleRecord::getDate).reversed()) // Most recent first
                .collect(Collectors.toList());

        // Calculate weighted average
        // Last 7 days: weight 3x
        // Days 8-14: weight 2x
        // Days 15-30: weight 1x
        LocalDate now = LocalDate.now();
        double weightedSum = 0.0;
        double totalWeight = 0.0;

        for (SaleRecord record : sortedRecords) {
            long daysAgo = record.getDaysAgo();
            double weight = getWeight(daysAgo);

            weightedSum += record.getQuantity() * weight;
            totalWeight += weight;
        }

        double weightedDailyAverage = totalWeight > 0 ? weightedSum / totalWeight : 0.0;

        BigDecimal dailyAverage = BigDecimal.valueOf(weightedDailyAverage)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal weeklyAverage = dailyAverage.multiply(BigDecimal.valueOf(7));
        BigDecimal monthlyAverage = dailyAverage.multiply(BigDecimal.valueOf(30));

        // Detect trend with detailed analysis
        int periodDays = calculatePeriodDays(sortedRecords);
        TrendAnalysis trendAnalysis = detectTrend(sortedRecords, periodDays);

        logger.info("Detailed velocity for SKU {}: Daily={} (weighted), Trend={} ({}%)",
                sku, dailyAverage, trendAnalysis.direction, trendAnalysis.percentage);

        return SalesVelocity.builder()
                .sku(sku)
                .dailyAverage(dailyAverage)
                .weeklyAverage(weeklyAverage)
                .monthlyAverage(monthlyAverage)
                .trend(trendAnalysis.direction)
                .trendPercentage(trendAnalysis.percentage)
                .lastCalculated(LocalDateTime.now())
                .calculationPeriodDays(periodDays)
                .dataSampleSize(sortedRecords.size())
                .build();
    }

    /**
     * Predict future sales based on velocity and trend
     *
     * @param velocity Sales velocity
     * @param days Number of days to predict
     * @return Predicted units to be sold
     */
    public BigDecimal predictSales(SalesVelocity velocity, int days) {
        if (velocity == null || velocity.getDailyAverage() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal basePrediction = velocity.getDailyAverage()
                .multiply(BigDecimal.valueOf(days));

        // Adjust for trend
        if (velocity.getTrend() == TrendDirection.INCREASING && velocity.getTrendPercentage() != null) {
            // Add trend percentage to prediction
            BigDecimal trendAdjustment = basePrediction
                    .multiply(velocity.getTrendPercentage().abs())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return basePrediction.add(trendAdjustment);
        } else if (velocity.getTrend() == TrendDirection.DECREASING && velocity.getTrendPercentage() != null) {
            // Subtract trend percentage from prediction
            BigDecimal trendAdjustment = basePrediction
                    .multiply(velocity.getTrendPercentage().abs())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return basePrediction.subtract(trendAdjustment).max(BigDecimal.ZERO);
        }

        return basePrediction;
    }

    // =====================================================
    // Private Helper Methods
    // =====================================================

    /**
     * Calculate the period in days covered by sales records
     */
    private int calculatePeriodDays(List<SaleRecord> records) {
        if (records.isEmpty()) {
            return 0;
        }

        LocalDate earliest = records.stream()
                .map(SaleRecord::getDate)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());

        LocalDate latest = records.stream()
                .map(SaleRecord::getDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());

        int days = (int) (latest.toEpochDay() - earliest.toEpochDay());
        return Math.max(days, 1); // At least 1 day
    }

    /**
     * Detect trend direction and strength
     * Compares recent period vs older period to determine if sales are trending up, down, or stable
     */
    private TrendAnalysis detectTrend(List<SaleRecord> sortedRecords, int totalPeriodDays) {
        if (sortedRecords.size() < 4 || totalPeriodDays < 14) {
            // Not enough data for trend analysis
            return new TrendAnalysis(TrendDirection.STABLE, BigDecimal.ZERO);
        }

        // Split records into two periods: recent and older
        LocalDate midpoint = LocalDate.now().minusDays(totalPeriodDays / 2);

        List<SaleRecord> recentPeriod = sortedRecords.stream()
                .filter(r -> r.getDate().isAfter(midpoint))
                .collect(Collectors.toList());

        List<SaleRecord> olderPeriod = sortedRecords.stream()
                .filter(r -> !r.getDate().isAfter(midpoint))
                .collect(Collectors.toList());

        if (recentPeriod.isEmpty() || olderPeriod.isEmpty()) {
            return new TrendAnalysis(TrendDirection.STABLE, BigDecimal.ZERO);
        }

        // Calculate average daily sales for each period
        int recentDays = calculatePeriodDays(recentPeriod);
        int olderDays = calculatePeriodDays(olderPeriod);

        double recentAvg = recentPeriod.stream()
                .mapToInt(SaleRecord::getQuantity)
                .sum() / (double) Math.max(recentDays, 1);

        double olderAvg = olderPeriod.stream()
                .mapToInt(SaleRecord::getQuantity)
                .sum() / (double) Math.max(olderDays, 1);

        // Calculate percentage change
        double percentageChange = 0.0;
        if (olderAvg > 0) {
            percentageChange = ((recentAvg - olderAvg) / olderAvg) * 100.0;
        }

        // Determine trend direction
        TrendDirection direction;
        if (Math.abs(percentageChange) < TREND_THRESHOLD) {
            direction = TrendDirection.STABLE;
        } else if (percentageChange > 0) {
            direction = TrendDirection.INCREASING;
        } else {
            direction = TrendDirection.DECREASING;
        }

        logger.debug("Trend analysis: Recent avg={}, Older avg={}, Change={}%, Direction={}",
                recentAvg, olderAvg, percentageChange, direction);

        return new TrendAnalysis(direction, BigDecimal.valueOf(percentageChange)
                .setScale(1, RoundingMode.HALF_UP));
    }

    /**
     * Get weight for a sale record based on how many days ago it occurred
     * More recent sales have higher weight
     */
    private double getWeight(long daysAgo) {
        if (daysAgo <= 7) {
            return 3.0; // Last week: 3x weight
        } else if (daysAgo <= 14) {
            return 2.0; // Week 2: 2x weight
        } else if (daysAgo <= 30) {
            return 1.0; // Weeks 3-4: 1x weight
        } else {
            return 0.5; // Older than 30 days: 0.5x weight
        }
    }

    /**
     * Create a zero velocity object for products with no sales
     */
    private SalesVelocity createZeroVelocity(String sku) {
        return SalesVelocity.builder()
                .sku(sku)
                .dailyAverage(BigDecimal.ZERO)
                .weeklyAverage(BigDecimal.ZERO)
                .monthlyAverage(BigDecimal.ZERO)
                .trend(TrendDirection.STABLE)
                .trendPercentage(BigDecimal.ZERO)
                .lastCalculated(LocalDateTime.now())
                .calculationPeriodDays(0)
                .dataSampleSize(0)
                .build();
    }

    /**
     * Internal class for trend analysis results
     */
    private static class TrendAnalysis {
        final TrendDirection direction;
        final BigDecimal percentage;

        TrendAnalysis(TrendDirection direction, BigDecimal percentage) {
            this.direction = direction;
            this.percentage = percentage;
        }
    }
}
