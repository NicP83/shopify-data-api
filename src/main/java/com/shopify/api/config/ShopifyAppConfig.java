package com.shopify.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Shopify app configuration properties
 */
@Configuration
@ConfigurationProperties(prefix = "shopify.app")
@Data
public class ShopifyAppConfig {

    private String apiKey;
    private String apiSecret;
    private String scopes;
    private String redirectUri;
    private String installUri;
    private String callbackUri;

    /**
     * Get OAuth authorization URL for a shop
     */
    public String getAuthorizationUrl(String shopDomain, String nonce) {
        return String.format(
            "https://%s/admin/oauth/authorize?client_id=%s&scope=%s&redirect_uri=%s&state=%s",
            shopDomain,
            apiKey,
            scopes,
            redirectUri,
            nonce
        );
    }

    /**
     * Get token exchange URL for a shop
     */
    public String getTokenExchangeUrl(String shopDomain) {
        return String.format("https://%s/admin/oauth/access_token", shopDomain);
    }
}
