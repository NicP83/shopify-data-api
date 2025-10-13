package com.shopify.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopify.api.client.MCPClient;
import com.shopify.api.model.ChannelSalesData;
import com.shopify.api.model.PeriodComparison;
import com.shopify.api.model.SalesAnalytics;
import com.shopify.api.model.SalesByChannelData;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * Get sales breakdown by channel for a specific period
     * Uses the new get_sales_by_channel MCP tool
     * @param period "1d", "7d", "30d", or "90d"
     * @return SalesByChannelData with breakdown by store and online
     */
    public Mono<SalesByChannelData> getSalesByChannel(String period) {
        if (!enabled) {
            logger.warn("CRS MCP is disabled, returning empty channel data");
            return Mono.just(createEmptyChannelData(period));
        }

        int days = parsePeriod(period);
        LocalDateTime now = LocalDateTime.now(SYDNEY_ZONE);
        LocalDateTime startDate = now.minusDays(days);
        LocalDateTime endDate = now;

        logger.info("Fetching CRS sales by channel for period: {} ({} days)", period, days);

        return getChannelSalesForPeriod(startDate, endDate, period)
                .flatMap(currentSales -> {
                    // Get previous period for YoY comparison
                    LocalDateTime prevStart = startDate.minusYears(1);
                    LocalDateTime prevEnd = endDate.minusYears(1);

                    return getChannelSalesForPeriod(prevStart, prevEnd, period)
                            .map(previousSales -> {
                                // Create YoY comparison for total revenue
                                PeriodComparison comparison = new PeriodComparison(
                                    currentSales.getTotalRevenue(),
                                    previousSales.getTotalRevenue()
                                );
                                currentSales.setYearOverYearComparison(comparison);

                                logger.info("Channel Sales for {}: ${} ({} orders), YoY: {}%",
                                    period, currentSales.getTotalRevenue(), currentSales.getTotalOrders(),
                                    comparison.getPercentageChange());

                                return currentSales;
                            })
                            .onErrorResume(e -> {
                                logger.warn("Failed to fetch previous period channel data: {}", e.getMessage());
                                return Mono.just(currentSales);
                            });
                })
                .onErrorResume(e -> {
                    logger.error("Error fetching channel sales for period {}: {}", period, e.getMessage(), e);
                    return Mono.just(createEmptyChannelData(period));
                });
    }

    /**
     * Get all sales by channel for all periods
     * @return Map of period -> SalesByChannelData
     */
    public Mono<Map<String, SalesByChannelData>> getAllSalesByChannel() {
        if (!enabled) {
            logger.warn("CRS MCP is disabled, returning empty channel data");
            return Mono.just(new HashMap<>());
        }

        logger.info("Fetching all CRS sales by channel");

        return Mono.zip(
            getSalesByChannel("1d"),
            getSalesByChannel("7d"),
            getSalesByChannel("30d"),
            getSalesByChannel("90d")
        ).map(tuple -> {
            Map<String, SalesByChannelData> result = new HashMap<>();
            result.put("1d", tuple.getT1());
            result.put("7d", tuple.getT2());
            result.put("30d", tuple.getT3());
            result.put("90d", tuple.getT4());
            return result;
        });
    }

    /**
     * Get channel sales data for a specific date range
     */
    private Mono<SalesByChannelData> getChannelSalesForPeriod(LocalDateTime startDate, LocalDateTime endDate, String period) {
        String startDateStr = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String endDateStr = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("start_date", startDateStr);
        arguments.put("end_date", endDateStr);

        logger.debug("Calling get_sales_by_channel: {} to {}", startDateStr, endDateStr);

        return mcpClient.callTool("get_sales_by_channel", arguments)
                .map(result -> parseChannelSalesResponse(result, startDate, endDate, period))
                .onErrorResume(e -> {
                    logger.error("Error calling get_sales_by_channel: {}", e.getMessage(), e);
                    return Mono.error(new RuntimeException("Failed to get channel sales", e));
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
     * Parse get_sales_by_channel response into SalesByChannelData
     * Expected format:
     * "Sales by Channel (2025-10-06 to 2025-10-13):
     *
     * IN-STORE SALES:
     *   The Hobbyman:
     *     Orders: 419
     *     Items: 1213
     *     Revenue: $33,369.28
     *
     *   Hearns Hobbies:
     *     Orders: 948
     *     Items: 2790
     *     Revenue: $91,585.01
     *
     *   In-Store Subtotal: 1367 orders, $124,954.29
     *
     * ONLINE SALES (Shopify):
     *   Orders: 236
     *   Revenue: $26,463.70
     *
     * ========================================
     * GRAND TOTAL: 1603 orders, $151,417.99"
     */
    private SalesByChannelData parseChannelSalesResponse(JsonNode result, LocalDateTime startDate, LocalDateTime endDate, String period) {
        try {
            SalesByChannelData data = new SalesByChannelData();
            data.setPeriod(period);
            data.setPeriodStart(startDate);
            data.setPeriodEnd(endDate);

            if (result.has("content") && result.get("content").isArray() && result.get("content").size() > 0) {
                JsonNode content = result.get("content").get(0);
                if (content.has("text")) {
                    String text = content.get("text").asText();
                    logger.debug("Channel Sales Response: {}", text);

                    // Parse The Hobbyman
                    Pattern hobbymanPattern = Pattern.compile("The Hobbyman:[\\s\\S]*?Orders:\\s*(\\d+)[\\s\\S]*?Items:\\s*(\\d+)[\\s\\S]*?Revenue:\\s*\\$([\\d,]+\\.\\d{2})");
                    Matcher hobbymanMatcher = hobbymanPattern.matcher(text);
                    if (hobbymanMatcher.find()) {
                        ChannelSalesData hobbyman = new ChannelSalesData();
                        hobbyman.setChannelName("The Hobbyman");
                        hobbyman.setOrderCount(Integer.parseInt(hobbymanMatcher.group(1)));
                        hobbyman.setItemCount(Integer.parseInt(hobbymanMatcher.group(2)));
                        hobbyman.setRevenue(new BigDecimal(hobbymanMatcher.group(3).replace(",", "")));
                        data.setHobbyman(hobbyman);
                    }

                    // Parse Hearns Hobbies
                    Pattern hearnsPattern = Pattern.compile("Hearns Hobbies:[\\s\\S]*?Orders:\\s*(\\d+)[\\s\\S]*?Items:\\s*(\\d+)[\\s\\S]*?Revenue:\\s*\\$([\\d,]+\\.\\d{2})");
                    Matcher hearnsMatcher = hearnsPattern.matcher(text);
                    if (hearnsMatcher.find()) {
                        ChannelSalesData hearns = new ChannelSalesData();
                        hearns.setChannelName("Hearns Hobbies");
                        hearns.setOrderCount(Integer.parseInt(hearnsMatcher.group(1)));
                        hearns.setItemCount(Integer.parseInt(hearnsMatcher.group(2)));
                        hearns.setRevenue(new BigDecimal(hearnsMatcher.group(3).replace(",", "")));
                        data.setHearnsHobbies(hearns);
                    }

                    // Parse Shopify
                    Pattern shopifyPattern = Pattern.compile("ONLINE SALES[\\s\\S]*?Orders:\\s*(\\d+)[\\s\\S]*?Revenue:\\s*\\$([\\d,]+\\.\\d{2})");
                    Matcher shopifyMatcher = shopifyPattern.matcher(text);
                    if (shopifyMatcher.find()) {
                        ChannelSalesData shopify = new ChannelSalesData();
                        shopify.setChannelName("Shopify");
                        shopify.setOrderCount(Integer.parseInt(shopifyMatcher.group(1)));
                        shopify.setItemCount(0); // Not provided for online
                        shopify.setRevenue(new BigDecimal(shopifyMatcher.group(2).replace(",", "")));
                        data.setShopify(shopify);
                    }

                    // Parse Grand Total
                    Pattern totalPattern = Pattern.compile("GRAND TOTAL:\\s*(\\d+)\\s*orders,\\s*\\$([\\d,]+\\.\\d{2})");
                    Matcher totalMatcher = totalPattern.matcher(text);
                    if (totalMatcher.find()) {
                        data.setTotalOrders(Integer.parseInt(totalMatcher.group(1)));
                        data.setTotalRevenue(new BigDecimal(totalMatcher.group(2).replace(",", "")));
                    }

                    // Calculate total items
                    int totalItems = 0;
                    if (data.getHobbyman() != null) totalItems += data.getHobbyman().getItemCount();
                    if (data.getHearnsHobbies() != null) totalItems += data.getHearnsHobbies().getItemCount();
                    data.setTotalItems(totalItems);

                    logger.info("Parsed channel sales - Total: ${}, Hobbyman: ${}, Hearns: ${}, Shopify: ${}",
                        data.getTotalRevenue(),
                        data.getHobbyman() != null ? data.getHobbyman().getRevenue() : "0",
                        data.getHearnsHobbies() != null ? data.getHearnsHobbies().getRevenue() : "0",
                        data.getShopify() != null ? data.getShopify().getRevenue() : "0");

                } else {
                    logger.warn("Channel sales response missing text field");
                }
            } else {
                logger.warn("Channel sales response has unexpected structure");
            }

            return data;

        } catch (Exception e) {
            logger.error("Error parsing channel sales response: {}", e.getMessage(), e);
            return createEmptyChannelData(period);
        }
    }

    /**
     * Create empty channel data object
     */
    private SalesByChannelData createEmptyChannelData(String period) {
        SalesByChannelData data = new SalesByChannelData();
        data.setPeriod(period);

        LocalDateTime now = LocalDateTime.now(SYDNEY_ZONE);
        int days = parsePeriod(period);
        data.setPeriodStart(now.minusDays(days));
        data.setPeriodEnd(now);

        data.setHobbyman(new ChannelSalesData("The Hobbyman", 0, 0, BigDecimal.ZERO));
        data.setHearnsHobbies(new ChannelSalesData("Hearns Hobbies", 0, 0, BigDecimal.ZERO));
        data.setShopify(new ChannelSalesData("Shopify", 0, 0, BigDecimal.ZERO));
        data.setTotalOrders(0);
        data.setTotalItems(0);
        data.setTotalRevenue(BigDecimal.ZERO);

        return data;
    }

    /**
     * Check if CRS integration is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}
