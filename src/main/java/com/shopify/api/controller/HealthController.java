package com.shopify.api.controller;

import com.shopify.api.client.ShopifyGraphQLClient;
import com.shopify.api.model.ApiResponse;
import com.shopify.api.util.RateLimiter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Health check and system status endpoints
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final ShopifyGraphQLClient graphQLClient;
    private final RateLimiter rateLimiter;

    public HealthController(ShopifyGraphQLClient graphQLClient, RateLimiter rateLimiter) {
        this.graphQLClient = graphQLClient;
        this.rateLimiter = rateLimiter;
    }

    /**
     * GET /api/health
     * Basic health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "Shopify Data API");
        health.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(ApiResponse.success(health));
    }

    /**
     * GET /api/status
     * Detailed system status including Shopify connection
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        Map<String, Object> status = new HashMap<>();

        // Check Shopify connection
        boolean shopifyConnected = graphQLClient.testConnection();

        status.put("service", "Shopify Data API");
        status.put("timestamp", System.currentTimeMillis());
        status.put("shopify_connected", shopifyConnected);
        status.put("rate_limiter_available_points", rateLimiter.getAvailablePoints());

        if (shopifyConnected) {
            return ResponseEntity.ok(ApiResponse.success("System operational", status));
        } else {
            return ResponseEntity.status(503)
                    .body(ApiResponse.error("Shopify connection failed", null));
        }
    }
}
