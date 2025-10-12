package com.shopify.api.controller;

import com.shopify.api.model.ApiResponse;
import com.shopify.api.model.SalesAnalytics;
import com.shopify.api.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
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
     * Validate period parameter
     */
    private boolean isValidPeriod(String period) {
        return period != null &&
               (period.equals("1d") || period.equals("7d") ||
                period.equals("30d") || period.equals("90d"));
    }
}
