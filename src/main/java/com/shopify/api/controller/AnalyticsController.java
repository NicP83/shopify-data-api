package com.shopify.api.controller;

import com.shopify.api.model.ApiResponse;
import com.shopify.api.model.SalesAnalytics;
import com.shopify.api.model.SalesByChannelData;
import com.shopify.api.service.AnalyticsService;
import com.shopify.api.service.CRSAnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * REST Controller for Analytics operations
 * Provides endpoints for sales analytics and reporting
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsController.class);

    private final AnalyticsService analyticsService;
    private final CRSAnalyticsService crsAnalyticsService;

    public AnalyticsController(AnalyticsService analyticsService,
                              CRSAnalyticsService crsAnalyticsService) {
        this.analyticsService = analyticsService;
        this.crsAnalyticsService = crsAnalyticsService;
    }

    /**
     * GET /api/analytics/sales
     * Get sales analytics for a specific period
     *
     * @param period Time period: "1d", "7d", "30d", or "90d"
     * @return Sales analytics with YoY comparison
     */
    @GetMapping("/sales")
    public ResponseEntity<ApiResponse<SalesAnalytics>> getSalesAnalytics(
            @RequestParam(defaultValue = "7d") String period) {

        logger.info("GET /api/analytics/sales - period: {}", period);

        try {
            // Validate period parameter
            if (!isValidPeriod(period)) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid period",
                        "Period must be one of: 1d, 7d, 30d, 90d"));
            }

            SalesAnalytics analytics = analyticsService.getSalesAnalytics(period);
            return ResponseEntity.ok(ApiResponse.success(analytics));
        } catch (Exception e) {
            logger.error("Error fetching sales analytics for period {}", period, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch sales analytics", e.getMessage()));
        }
    }

    /**
     * GET /api/analytics/sales/all
     * Get sales analytics for all periods (1d, 7d, 30d, 90d)
     *
     * @return Map of period -> analytics for all periods
     */
    @GetMapping("/sales/all")
    public ResponseEntity<ApiResponse<Map<String, SalesAnalytics>>> getAllSalesAnalytics() {

        logger.info("GET /api/analytics/sales/all");

        try {
            Map<String, SalesAnalytics> allAnalytics = analyticsService.getAllSalesAnalytics();
            return ResponseEntity.ok(ApiResponse.success(allAnalytics));
        } catch (Exception e) {
            logger.error("Error fetching all sales analytics", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch sales analytics", e.getMessage()));
        }
    }

    /**
     * GET /api/analytics/instore/sales
     * Get in-store sales analytics from CRS for a specific period
     *
     * @param period Time period: "1d", "7d", "30d", or "90d"
     * @return In-store sales analytics with YoY comparison
     */
    @GetMapping("/instore/sales")
    public Mono<ResponseEntity<ApiResponse<SalesAnalytics>>> getInstoreSalesAnalytics(
            @RequestParam(defaultValue = "7d") String period) {

        logger.info("GET /api/analytics/instore/sales - period: {}", period);

        // Validate period parameter
        if (!isValidPeriod(period)) {
            return Mono.just(ResponseEntity.badRequest()
                .body(ApiResponse.error("Invalid period",
                    "Period must be one of: 1d, 7d, 30d, 90d")));
        }

        return crsAnalyticsService.getSalesAnalytics(period)
                .map(analytics -> ResponseEntity.ok(ApiResponse.success(analytics)))
                .onErrorResume(e -> {
                    logger.error("Error fetching in-store sales analytics for period {}", period, e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(ApiResponse.error("Failed to fetch in-store sales analytics", e.getMessage())));
                });
    }

    /**
     * GET /api/analytics/instore/sales/all
     * Get in-store sales analytics from CRS for all periods (1d, 7d, 30d, 90d)
     *
     * @return Map of period -> analytics for all periods
     */
    @GetMapping("/instore/sales/all")
    public Mono<ResponseEntity<ApiResponse<Map<String, SalesAnalytics>>>> getAllInstoreSalesAnalytics() {

        logger.info("GET /api/analytics/instore/sales/all");

        return crsAnalyticsService.getAllSalesAnalytics()
                .map(allAnalytics -> ResponseEntity.ok(ApiResponse.success(allAnalytics)))
                .onErrorResume(e -> {
                    logger.error("Error fetching all in-store sales analytics", e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(ApiResponse.error("Failed to fetch in-store sales analytics", e.getMessage())));
                });
    }

    /**
     * GET /api/analytics/channels
     * Get sales breakdown by channel (Hobbyman, Hearns, Shopify) for a specific period
     *
     * @param period Time period: "1d", "7d", "30d", or "90d"
     * @return Sales breakdown by all channels with YoY comparison
     */
    @GetMapping("/channels")
    public Mono<ResponseEntity<ApiResponse<SalesByChannelData>>> getSalesByChannel(
            @RequestParam(defaultValue = "7d") String period) {

        logger.info("GET /api/analytics/channels - period: {}", period);

        // Validate period parameter
        if (!isValidPeriod(period)) {
            return Mono.just(ResponseEntity.badRequest()
                .body(ApiResponse.error("Invalid period",
                    "Period must be one of: 1d, 7d, 30d, 90d")));
        }

        return crsAnalyticsService.getSalesByChannel(period)
                .map(channelData -> ResponseEntity.ok(ApiResponse.success(channelData)))
                .onErrorResume(e -> {
                    logger.error("Error fetching sales by channel for period {}", period, e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(ApiResponse.error("Failed to fetch channel sales", e.getMessage())));
                });
    }

    /**
     * GET /api/analytics/channels/all
     * Get sales by channel for all periods (1d, 7d, 30d, 90d)
     *
     * @return Map of period -> channel sales for all periods
     */
    @GetMapping("/channels/all")
    public Mono<ResponseEntity<ApiResponse<Map<String, SalesByChannelData>>>> getAllSalesByChannel() {

        logger.info("GET /api/analytics/channels/all");

        return crsAnalyticsService.getAllSalesByChannel()
                .map(allChannelData -> ResponseEntity.ok(ApiResponse.success(allChannelData)))
                .onErrorResume(e -> {
                    logger.error("Error fetching all channel sales", e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(ApiResponse.error("Failed to fetch channel sales", e.getMessage())));
                });
    }

    /**
     * Validate period parameter
     */
    private boolean isValidPeriod(String period) {
        return period != null &&
               (period.equals("1d") || period.equals("7d") ||
                period.equals("30d") || period.equals("90d"));
    }
}
