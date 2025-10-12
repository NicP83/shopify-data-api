package com.shopify.api.service;

import com.shopify.api.model.PeriodComparison;
import com.shopify.api.model.SalesAnalytics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service for calculating sales analytics and metrics
 */
@Service
public class AnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);
    private static final DateTimeFormatter SHOPIFY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneId STORE_TIMEZONE = ZoneId.of("Australia/Sydney");

    private final OrderService orderService;

    public AnalyticsService(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Get sales analytics for a specific period
     * @param period Period identifier: "1d", "7d", "30d", "90d"
     * @return Sales analytics with YoY comparison
     */
    public SalesAnalytics getSalesAnalytics(String period) {
        logger.info("Calculating sales analytics for period: {}", period);

        // Use Australian timezone for date calculations
        LocalDateTime now = LocalDateTime.now(STORE_TIMEZONE);
        // Set to end of current day to include all of today's orders
        LocalDateTime periodEnd = now.toLocalDate().atTime(23, 59, 59);
        LocalDateTime periodStart = calculatePeriodStart(periodEnd, period);

        // Current period
        SalesAnalytics analytics = calculatePeriodAnalytics(period, periodStart, periodEnd);

        // Previous year same period
        LocalDateTime previousYearEnd = periodEnd.minusYears(1);
        LocalDateTime previousYearStart = periodStart.minusYears(1);
        SalesAnalytics previousYearAnalytics = calculatePeriodAnalytics(
            period + " (last year)",
            previousYearStart,
            previousYearEnd
        );

        // Calculate YoY comparison
        PeriodComparison comparison = new PeriodComparison(
            analytics.getTotalSales(),
            previousYearAnalytics.getTotalSales()
        );
        analytics.setYearOverYearComparison(comparison);

        logger.info("Analytics calculated - Period: {}, Sales: {}, Orders: {}, YoY: {}%",
            period, analytics.getTotalSales(), analytics.getOrderCount(),
            comparison.getPercentageChange());

        return analytics;
    }

    /**
     * Get all sales analytics (1d, 7d, 30d, 90d) at once
     * @return Map of period -> analytics
     */
    public Map<String, SalesAnalytics> getAllSalesAnalytics() {
        logger.info("Calculating all sales analytics");

        Map<String, SalesAnalytics> allAnalytics = new LinkedHashMap<>();
        allAnalytics.put("1d", getSalesAnalytics("1d"));
        allAnalytics.put("7d", getSalesAnalytics("7d"));
        allAnalytics.put("30d", getSalesAnalytics("30d"));
        allAnalytics.put("90d", getSalesAnalytics("90d"));

        return allAnalytics;
    }

    /**
     * Calculate analytics for a specific time period
     */
    private SalesAnalytics calculatePeriodAnalytics(String period, LocalDateTime start, LocalDateTime end) {
        SalesAnalytics analytics = new SalesAnalytics(period, start, end);

        try {
            // Format dates for Shopify API (simple date format: yyyy-MM-dd)
            String startDateStr = start.toLocalDate().format(SHOPIFY_DATE_FORMATTER);
            String endDateStr = end.toLocalDate().format(SHOPIFY_DATE_FORMATTER);

            logger.info("Fetching orders for period {} from {} to {} (Australian timezone)",
                period, startDateStr, endDateStr);

            // Fetch orders for the period
            Map<String, Object> ordersData = orderService.getOrdersByDateRange(startDateStr, endDateStr);

            // Parse and aggregate data
            if (ordersData != null && ordersData.containsKey("orders")) {
                Map<String, Object> orders = (Map<String, Object>) ordersData.get("orders");
                List<Map<String, Object>> edges = (List<Map<String, Object>>) orders.get("edges");

                if (edges != null) {
                    BigDecimal totalSales = BigDecimal.ZERO;
                    BigDecimal totalFreight = BigDecimal.ZERO;
                    BigDecimal totalDiscounts = BigDecimal.ZERO;
                    int orderCount = 0;
                    String currencyCode = "AUD"; // Default to AUD for Australian store

                    for (Map<String, Object> edge : edges) {
                        Map<String, Object> node = (Map<String, Object>) edge.get("node");

                        // Extract total price
                        Map<String, Object> totalPriceSet = (Map<String, Object>) node.get("totalPriceSet");
                        if (totalPriceSet != null) {
                            Map<String, Object> shopMoney = (Map<String, Object>) totalPriceSet.get("shopMoney");
                            if (shopMoney != null) {
                                String amount = (String) shopMoney.get("amount");
                                if (amount != null && !amount.isEmpty()) {
                                    totalSales = totalSales.add(new BigDecimal(amount));
                                }
                                // Update currency code from actual order data
                                String orderCurrency = (String) shopMoney.get("currencyCode");
                                if (orderCurrency != null && !orderCurrency.isEmpty()) {
                                    currencyCode = orderCurrency;
                                }
                            }
                        }

                        // Extract shipping price
                        Map<String, Object> shippingPriceSet = (Map<String, Object>) node.get("totalShippingPriceSet");
                        if (shippingPriceSet != null) {
                            Map<String, Object> shopMoney = (Map<String, Object>) shippingPriceSet.get("shopMoney");
                            if (shopMoney != null) {
                                String amount = (String) shopMoney.get("amount");
                                if (amount != null && !amount.isEmpty()) {
                                    totalFreight = totalFreight.add(new BigDecimal(amount));
                                }
                            }
                        }

                        // Extract discounts
                        Map<String, Object> discountsSet = (Map<String, Object>) node.get("totalDiscountsSet");
                        if (discountsSet != null) {
                            Map<String, Object> shopMoney = (Map<String, Object>) discountsSet.get("shopMoney");
                            if (shopMoney != null) {
                                String amount = (String) shopMoney.get("amount");
                                if (amount != null && !amount.isEmpty()) {
                                    totalDiscounts = totalDiscounts.add(new BigDecimal(amount));
                                }
                            }
                        }

                        orderCount++;
                    }

                    // Set calculated values
                    analytics.setTotalSales(totalSales);
                    analytics.setTotalFreight(totalFreight);
                    analytics.setTotalDiscounts(totalDiscounts);
                    analytics.setOrderCount(orderCount);
                    analytics.setCurrencyCode(currencyCode);
                    analytics.calculateAverageSale();

                    logger.info("Analytics calculated for period {}: {} orders, Total Sales: {} {}, Avg Sale: {} {}, Freight: {} {}, Discounts: {} {}",
                        period, orderCount,
                        totalSales, currencyCode,
                        analytics.getAverageSale(), currencyCode,
                        totalFreight, currencyCode,
                        totalDiscounts, currencyCode);
                } else {
                    logger.warn("No orders found for period {} ({} to {})", period, startDateStr, endDateStr);
                }
            } else {
                logger.warn("No orders data returned for period {}", period);
            }
        } catch (Exception e) {
            logger.error("Error calculating analytics for period {}: {}", period, e.getMessage(), e);
            // Return analytics with zero values on error
        }

        return analytics;
    }

    /**
     * Calculate the start date for a given period
     * Returns start of day for the beginning of the period
     */
    private LocalDateTime calculatePeriodStart(LocalDateTime end, String period) {
        int days = switch (period.toLowerCase()) {
            case "1d" -> 1;
            case "7d" -> 7;
            case "30d" -> 30;
            case "90d" -> 90;
            default -> {
                logger.warn("Unknown period: {}, defaulting to 7d", period);
                yield 7;
            }
        };

        // Go back the specified number of days and start at beginning of that day
        return end.minusDays(days).toLocalDate().atStartOfDay();
    }
}
