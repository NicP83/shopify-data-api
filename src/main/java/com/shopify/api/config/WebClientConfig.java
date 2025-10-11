package com.shopify.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for WebClient used to make HTTP requests to Shopify API
 */
@Configuration
public class WebClientConfig {

    private final ShopifyConfig shopifyConfig;

    public WebClientConfig(ShopifyConfig shopifyConfig) {
        this.shopifyConfig = shopifyConfig;
    }

    /**
     * Creates a pre-configured WebClient for Shopify GraphQL API calls
     * Includes:
     * - Base URL to Shopify GraphQL endpoint
     * - Authorization header with access token
     * - Content-Type header for JSON
     */
    @Bean
    public WebClient shopifyWebClient() {
        return WebClient.builder()
                .baseUrl(shopifyConfig.getGraphQLEndpoint())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Shopify-Access-Token", shopifyConfig.getAccessToken())
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024)) // 16MB buffer for large responses
                .build();
    }
}
