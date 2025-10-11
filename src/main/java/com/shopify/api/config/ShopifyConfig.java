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
     * Constructs the storefront product URL
     * Format: https://{shop-url}/products/{handle}
     * @param handle Product handle (URL-friendly identifier)
     * @return Full product URL
     */
    public String getProductUrl(String handle) {
        return String.format("https://%s/products/%s", shopUrl, handle);
    }

    /**
     * Constructs a cart permalink URL for adding a product to cart
     * Format: https://{shop-url}/cart/{variant-id}:{quantity}
     * @param variantId The product variant ID (numeric part only, e.g., "6585417925")
     * @param quantity Quantity to add to cart
     * @return Cart permalink URL
     */
    public String getAddToCartUrl(String variantId, int quantity) {
        // Extract numeric ID from GraphQL ID format (gid://shopify/ProductVariant/6585417925)
        String numericId = variantId;
        if (variantId.contains("/")) {
            numericId = variantId.substring(variantId.lastIndexOf("/") + 1);
        }
        return String.format("https://%s/cart/%s:%d", shopUrl, numericId, quantity);
    }

    /**
     * Constructs a cart permalink URL for adding a product to cart (default quantity of 1)
     * @param variantId The product variant ID
     * @return Cart permalink URL
     */
    public String getAddToCartUrl(String variantId) {
        return getAddToCartUrl(variantId, 1);
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
