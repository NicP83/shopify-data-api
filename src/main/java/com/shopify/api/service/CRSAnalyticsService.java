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
     * Expected format: "Row 1: { \"OrderCount\": 1367, \"TotalSales\": 124954.29, \"AvgSale\": 32.93 }"
     */
    private void parseSalesFromText(String text, SalesAnalytics analytics) {
        try {
            logger.debug("Parsing CRS text response: {}", text);

            // Extract JSON object from text
            // Format: "Row 1: { ... }"
            int jsonStart = text.indexOf('{');
            int jsonEnd = text.lastIndexOf('}');

            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                String jsonStr = text.substring(jsonStart, jsonEnd + 1);
                logger.debug("Extracted JSON: {}", jsonStr);

                // Parse JSON using Jackson
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                JsonNode jsonNode = mapper.readTree(jsonStr);

                // Extract values from JSON
                if (jsonNode.has("OrderCount")) {
                    analytics.setOrderCount(jsonNode.get("OrderCount").asInt());
                } else {
                    analytics.setOrderCount(0);
                }

                if (jsonNode.has("TotalSales")) {
                    analytics.setTotalSales(new BigDecimal(jsonNode.get("TotalSales").asDouble()).setScale(2, RoundingMode.HALF_UP));
                } else {
                    analytics.setTotalSales(BigDecimal.ZERO);
                }

                if (jsonNode.has("AvgSale")) {
                    analytics.setAverageSale(new BigDecimal(jsonNode.get("AvgSale").asDouble()).setScale(2, RoundingMode.HALF_UP));
                } else if (analytics.getOrderCount() > 0 && analytics.getTotalSales() != null) {
                    // Calculate average from total and count
                    analytics.calculateAverageSale();
                } else {
                    analytics.setAverageSale(BigDecimal.ZERO);
                }

                // Set defaults for freight and discounts (not applicable for in-store)
                analytics.setTotalFreight(BigDecimal.ZERO);
                analytics.setTotalDiscounts(BigDecimal.ZERO);

                logger.info("Successfully parsed CRS data - Orders: {}, Sales: ${}, AvgSale: ${}",
                    analytics.getOrderCount(), analytics.getTotalSales(), analytics.getAverageSale());

            } else {
                logger.warn("Could not find JSON object in text: {}", text);
                setDefaultValues(analytics);
            }

        } catch (Exception e) {
            logger.error("Error parsing sales from text: {}", e.getMessage(), e);
            setDefaultValues(analytics);
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
