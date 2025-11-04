package com.shopify.api.service;

import com.shopify.api.config.ShopifyAppConfig;
import org.apache.commons.codec.digest.HmacUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Service for Shopify OAuth flow
 */
@Service
public class ShopifyOAuthService {

    private static final Logger logger = LoggerFactory.getLogger(ShopifyOAuthService.class);

    private final ShopifyAppConfig shopifyAppConfig;
    private final RestTemplate restTemplate;

    @Autowired
    public ShopifyOAuthService(ShopifyAppConfig shopifyAppConfig) {
        this.shopifyAppConfig = shopifyAppConfig;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Generate random nonce for OAuth state parameter
     */
    public String generateNonce() {
        return UUID.randomUUID().toString();
    }

    /**
     * Validate shop domain format
     */
    public boolean isValidShopDomain(String shopDomain) {
        if (shopDomain == null || shopDomain.isBlank()) {
            return false;
        }
        // Must end with .myshopify.com
        return shopDomain.matches("^[a-zA-Z0-9][a-zA-Z0-9\\-]*\\.myshopify\\.com$");
    }

    /**
     * Verify HMAC signature from Shopify callback
     */
    public boolean verifyHmac(Map<String, String> params, String hmac) {
        try {
            // Build query string without hmac parameter
            StringBuilder message = new StringBuilder();
            params.entrySet().stream()
                .filter(entry -> !entry.getKey().equals("hmac") && !entry.getKey().equals("signature"))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (message.length() > 0) {
                        message.append("&");
                    }
                    message.append(entry.getKey()).append("=").append(entry.getValue());
                });

            // Calculate HMAC-SHA256
            String calculatedHmac = new HmacUtils("HmacSHA256", shopifyAppConfig.getApiSecret())
                .hmacHex(message.toString());

            logger.debug("HMAC verification - Expected: {}, Calculated: {}", hmac, calculatedHmac);

            return calculatedHmac.equals(hmac);

        } catch (Exception e) {
            logger.error("HMAC verification failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Exchange authorization code for access token
     */
    public String exchangeCodeForToken(String shopDomain, String code) {
        try {
            String tokenUrl = shopifyAppConfig.getTokenExchangeUrl(shopDomain);

            // Build request body
            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("client_id", shopifyAppConfig.getApiKey());
            requestBody.add("client_secret", shopifyAppConfig.getApiSecret());
            requestBody.add("code", code);

            // Send POST request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);

            logger.info("Exchanging authorization code for access token: {}", shopDomain);

            ResponseEntity<Map> response = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                request,
                Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String accessToken = (String) response.getBody().get("access_token");
                logger.info("Successfully obtained access token for shop: {}", shopDomain);
                return accessToken;
            }

            logger.error("Failed to exchange code for token. Status: {}", response.getStatusCode());
            throw new RuntimeException("Token exchange failed");

        } catch (Exception e) {
            logger.error("Error exchanging code for token: {}", e.getMessage(), e);
            throw new RuntimeException("OAuth token exchange failed", e);
        }
    }

    /**
     * Fetch shop details from Shopify API
     */
    public Map<String, Object> fetchShopDetails(String shopDomain, String accessToken) {
        try {
            String shopUrl = String.format("https://%s/admin/api/2024-01/shop.json", shopDomain);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Shopify-Access-Token", accessToken);

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                shopUrl,
                HttpMethod.GET,
                request,
                Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (Map<String, Object>) response.getBody().get("shop");
            }

            logger.warn("Failed to fetch shop details for: {}", shopDomain);
            return Map.of();

        } catch (Exception e) {
            logger.error("Error fetching shop details: {}", e.getMessage());
            return Map.of();
        }
    }
}
