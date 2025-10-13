package com.shopify.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopify.api.client.MCPClient;
import com.shopify.api.model.PeriodComparison;
import com.shopify.api.model.SalesAnalytics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for retrieving in-store sales analytics from CRS ERP via MCP
 * Provides sales data for different time periods (1d, 7d, 30d, 90d)
 */
@Service
public class CRSAnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(CRSAnalyticsService.class);
    private static final ZoneId SYDNEY_ZONE = ZoneId.of("Australia/Sydney");
    private static final DateTimeFormatter SQL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MCPClient mcpClient;
    private final boolean enabled;

    public CRSAnalyticsService(MCPClient mcpClient,
                              @Value("${crs.mcp.enabled:true}") boolean enabled) {
        this.mcpClient = mcpClient;
        this.enabled = enabled;
        logger.info("CRSAnalyticsService initialized - Enabled: {}", enabled);
    }

    /**
     * Get sales analytics for a specific period
     * @param period "1d", "7d", "30d", or "90d"
     * @return SalesAnalytics with in-store sales data
     */
    public Mono<SalesAnalytics> getSalesAnalytics(String period) {
        if (!enabled) {
            logger.warn("CRS MCP is disabled, returning empty analytics");
            return Mono.just(createEmptyAnalytics(period));
        }

        int days = parsePeriod(period);
        LocalDateTime now = LocalDateTime.now(SYDNEY_ZONE);
        LocalDateTime startDate = now.minusDays(days);
        LocalDateTime endDate = now;

        logger.info("Fetching CRS sales for period: {} ({} days)", period, days);

        // Get current period data
        return getSalesForPeriod(startDate, endDate, period)
                .flatMap(currentSales -> {
                    // Get previous period for YoY comparison
                    LocalDateTime prevStart = startDate.minusYears(1);
                    LocalDateTime prevEnd = endDate.minusYears(1);

                    return getSalesForPeriod(prevStart, prevEnd, period)
                            .map(previousSales -> {
                                // Create YoY comparison
                                PeriodComparison comparison = new PeriodComparison(
                                    currentSales.getTotalSales(),
                                    previousSales.getTotalSales()
                                );
                                currentSales.setYearOverYearComparison(comparison);

                                logger.info("CRS Analytics for {}: ${} ({} orders), YoY: {}%",
                                    period, currentSales.getTotalSales(), currentSales.getOrderCount(),
                                    comparison.getPercentageChange());

                                return currentSales;
                            })
                            .onErrorResume(e -> {
                                // If previous period fails, return current data without YoY
                                logger.warn("Failed to fetch previous period data: {}", e.getMessage());
                                return Mono.just(currentSales);
                            });
                })
                .onErrorResume(e -> {
                    logger.error("Error fetching CRS sales for period {}: {}", period, e.getMessage(), e);
                    return Mono.just(createEmptyAnalytics(period));
                });
    }

    /**
     * Get all sales analytics periods at once
     * @return Map of period -> SalesAnalytics
     */
    public Mono<Map<String, SalesAnalytics>> getAllSalesAnalytics() {
        if (!enabled) {
            logger.warn("CRS MCP is disabled, returning empty analytics");
            return Mono.just(new HashMap<>());
        }

        logger.info("Fetching all CRS sales analytics");

        return Mono.zip(
            getSalesAnalytics("1d"),
            getSalesAnalytics("7d"),
            getSalesAnalytics("30d"),
            getSalesAnalytics("90d")
        ).map(tuple -> {
            Map<String, SalesAnalytics> result = new HashMap<>();
            result.put("1d", tuple.getT1());
            result.put("7d", tuple.getT2());
            result.put("30d", tuple.getT3());
            result.put("90d", tuple.getT4());
            return result;
        });
    }

    /**
     * Get sales data for a specific date range
     */
    private Mono<SalesAnalytics> getSalesForPeriod(LocalDateTime startDate, LocalDateTime endDate, String period) {
        String query = buildSalesQuery(startDate, endDate);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("query", query);

        logger.debug("Executing CRS query: {}", query);

        return mcpClient.callTool("query_database", arguments)
                .map(result -> parseCRSResponse(result, startDate, endDate, period))
                .onErrorResume(e -> {
                    logger.error("Error executing CRS query: {}", e.getMessage(), e);
                    return Mono.error(new RuntimeException("Failed to query CRS database", e));
                });
    }

    /**
     * Build SQL query for sales data
     */
    private String buildSalesQuery(LocalDateTime startDate, LocalDateTime endDate) {
        String start = startDate.format(SQL_DATE_FORMAT);
        String end = endDate.format(SQL_DATE_FORMAT);

        return String.format("""
            SELECT
              COUNT(DISTINCT h.DocNum) as OrderCount,
              ISNULL(SUM(d.SaleAmount), 0) as TotalSales,
              ISNULL(AVG(d.SaleAmount), 0) as AvgSale,
              ISNULL(SUM(d.CostOfGoods), 0) as TotalCost
            FROM InvoiceHeader h
            LEFT JOIN InvoiceDetail d ON h.DocNum = d.DocNum
            WHERE h.Date >= '%s'
              AND h.Date <= '%s'
              AND h.Completed = 1
            """, start, end);
    }

    /**
     * Parse CRS database response into SalesAnalytics
     */
    private SalesAnalytics parseCRSResponse(JsonNode result, LocalDateTime startDate, LocalDateTime endDate, String period) {
        try {
            SalesAnalytics analytics = new SalesAnalytics();
            analytics.setPeriod(period);
            analytics.setPeriodStart(startDate);
            analytics.setPeriodEnd(endDate);
            analytics.setCurrencyCode("AUD");

            // Handle MCP response structure
            // Expected: result.content[0].text contains the query results
            if (result.has("content") && result.get("content").isArray() && result.get("content").size() > 0) {
                JsonNode content = result.get("content").get(0);
                if (content.has("text")) {
                    String text = content.get("text").asText();
                    logger.debug("CRS Response text: {}", text);

                    // Parse the text response - it may be formatted as a table or JSON
                    parseSalesFromText(text, analytics);
                } else {
                    logger.warn("CRS response missing text field");
                    setDefaultValues(analytics);
                }
            } else {
                logger.warn("CRS response has unexpected structure: {}", result);
                setDefaultValues(analytics);
            }

            return analytics;

        } catch (Exception e) {
            logger.error("Error parsing CRS response: {}", e.getMessage(), e);
            return createEmptyAnalytics(period);
        }
    }

    /**
     * Parse sales data from text response
     */
    private void parseSalesFromText(String text, SalesAnalytics analytics) {
        try {
            // Try to parse structured data from text
            // The MCP returns results in a formatted table or JSON-like structure

            // Look for patterns like "OrderCount: 1137" or "TotalSales: 108189.18"
            String orderCountStr = extractValue(text, "OrderCount");
            String totalSalesStr = extractValue(text, "TotalSales");
            String avgSaleStr = extractValue(text, "AvgSale");

            if (orderCountStr != null) {
                analytics.setOrderCount(Integer.parseInt(orderCountStr));
            } else {
                analytics.setOrderCount(0);
            }

            if (totalSalesStr != null) {
                analytics.setTotalSales(new BigDecimal(totalSalesStr).setScale(2, RoundingMode.HALF_UP));
            } else {
                analytics.setTotalSales(BigDecimal.ZERO);
            }

            if (avgSaleStr != null) {
                analytics.setAverageSale(new BigDecimal(avgSaleStr).setScale(2, RoundingMode.HALF_UP));
            } else if (analytics.getOrderCount() > 0 && analytics.getTotalSales() != null) {
                // Calculate average from total and count
                analytics.calculateAverageSale();
            } else {
                analytics.setAverageSale(BigDecimal.ZERO);
            }

            // Set defaults for freight and discounts (not applicable for in-store)
            analytics.setTotalFreight(BigDecimal.ZERO);
            analytics.setTotalDiscounts(BigDecimal.ZERO);

            logger.debug("Parsed CRS data - Orders: {}, Sales: ${}",
                analytics.getOrderCount(), analytics.getTotalSales());

        } catch (Exception e) {
            logger.error("Error parsing sales from text: {}", e.getMessage());
            setDefaultValues(analytics);
        }
    }

    /**
     * Extract numeric value from text response
     */
    private String extractValue(String text, String fieldName) {
        try {
            // Try different patterns:
            // 1. "FieldName: 123.45"
            // 2. "FieldName | 123.45"
            // 3. Row format with field position

            String pattern1 = fieldName + ":\\s*([0-9.]+)";
            java.util.regex.Pattern p1 = java.util.regex.Pattern.compile(pattern1, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m1 = p1.matcher(text);
            if (m1.find()) {
                return m1.group(1);
            }

            String pattern2 = fieldName + "\\s*\\|\\s*([0-9.]+)";
            java.util.regex.Pattern p2 = java.util.regex.Pattern.compile(pattern2, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m2 = p2.matcher(text);
            if (m2.find()) {
                return m2.group(1);
            }

            // Try to find the field in a table row
            String pattern3 = fieldName + "[^0-9]*([0-9.]+)";
            java.util.regex.Pattern p3 = java.util.regex.Pattern.compile(pattern3, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m3 = p3.matcher(text);
            if (m3.find()) {
                return m3.group(1);
            }

            return null;
        } catch (Exception e) {
            logger.error("Error extracting value for {}: {}", fieldName, e.getMessage());
            return null;
        }
    }

    /**
     * Set default values for analytics
     */
    private void setDefaultValues(SalesAnalytics analytics) {
        if (analytics.getOrderCount() == null) {
            analytics.setOrderCount(0);
        }
        if (analytics.getTotalSales() == null) {
            analytics.setTotalSales(BigDecimal.ZERO);
        }
        if (analytics.getAverageSale() == null) {
            analytics.setAverageSale(BigDecimal.ZERO);
        }
        if (analytics.getTotalFreight() == null) {
            analytics.setTotalFreight(BigDecimal.ZERO);
        }
        if (analytics.getTotalDiscounts() == null) {
            analytics.setTotalDiscounts(BigDecimal.ZERO);
        }
    }

    /**
     * Parse period string to number of days
     */
    private int parsePeriod(String period) {
        return switch (period.toLowerCase()) {
            case "1d" -> 1;
            case "7d" -> 7;
            case "30d" -> 30;
            case "90d" -> 90;
            default -> {
                logger.warn("Unknown period: {}, defaulting to 7 days", period);
                yield 7;
            }
        };
    }

    /**
     * Create empty analytics object
     */
    private SalesAnalytics createEmptyAnalytics(String period) {
        SalesAnalytics analytics = new SalesAnalytics();
        analytics.setPeriod(period);
        analytics.setOrderCount(0);
        analytics.setTotalSales(BigDecimal.ZERO);
        analytics.setAverageSale(BigDecimal.ZERO);
        analytics.setTotalFreight(BigDecimal.ZERO);
        analytics.setTotalDiscounts(BigDecimal.ZERO);
        analytics.setCurrencyCode("AUD");

        LocalDateTime now = LocalDateTime.now(SYDNEY_ZONE);
        int days = parsePeriod(period);
        analytics.setPeriodStart(now.minusDays(days));
        analytics.setPeriodEnd(now);

        return analytics;
    }

    /**
     * Check if CRS integration is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}
