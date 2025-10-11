package com.shopify.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Shopify API connection
 * Maps values from application.yml under 'shopify' prefix
 */
@Configuration
@ConfigurationProperties(prefix = "shopify")
@Data
public class ShopifyConfig {

    /**
     * Shopify store URL (e.g., mystore.myshopify.com)
     */
    private String shopUrl;

    /**
     * Admin API Access Token
     */
    private String accessToken;

    /**
     * API Version (e.g., 2025-01)
     */
    private String apiVersion;

    /**
     * Rate limiting configuration
     */
    private RateLimit rateLimit = new RateLimit();

    /**
     * Constructs the full GraphQL endpoint URL
     * Format: https://{shop-url}/admin/api/{api-version}/graphql.json
     */
    public String getGraphQLEndpoint() {
        return String.format("https://%s/admin/api/%s/graphql.json",
                shopUrl, apiVersion);
    }

    /**
     * Rate limiting configuration nested class
     */
    @Data
    public static class RateLimit {
        /**
         * Maximum points per second based on Shopify plan
         * Basic: 100, Advanced: 200, Plus: 1000, Enterprise: 2000
         */
        private int maxPointsPerSecond = 100;

        /**
         * Maximum number of retries on rate limit
         */
        private int maxRetries = 3;

        /**
         * Initial backoff delay in milliseconds
         */
        private long initialBackoffMs = 1000;
    }
}
