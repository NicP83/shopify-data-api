package com.shopify.api.controller;

import com.shopify.api.model.ApiResponse;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.service.ShopifyShopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Temporary controller for manual shop registration
 * Used for custom app authentication (non-OAuth flow)
 */
@RestController
@RequestMapping("/api/admin/shops")
public class ShopRegistrationController {

    private static final Logger logger = LoggerFactory.getLogger(ShopRegistrationController.class);

    private final ShopifyShopService shopifyShopService;

    @Autowired
    public ShopRegistrationController(ShopifyShopService shopifyShopService) {
        this.shopifyShopService = shopifyShopService;
    }

    /**
     * POST /api/admin/shops/register
     * Manually register a shop (for custom app authentication)
     *
     * Request body:
     * {
     *   "shopDomain": "hearnshobbies.myshopify.com",
     *   "accessToken": "shpat_...",
     *   "scope": "read_products,write_script_tags"
     * }
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<ShopifyShop>> registerShop(
            @RequestBody ShopRegistrationRequest request) {

        logger.info("Manual shop registration request for: {}", request.getShopDomain());

        try {
            // Validate input
            if (request.getShopDomain() == null || request.getShopDomain().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid shop domain", "Shop domain is required"));
            }

            if (request.getAccessToken() == null || request.getAccessToken().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid access token", "Access token is required"));
            }

            String scope = request.getScope() != null && !request.getScope().isEmpty()
                ? request.getScope()
                : "read_products,write_script_tags";

            // Register shop
            ShopifyShop shop = shopifyShopService.saveShop(
                request.getShopDomain(),
                request.getAccessToken(),
                scope
            );

            logger.info("Shop registered successfully: {}", request.getShopDomain());

            return ResponseEntity.ok(ApiResponse.success("Shop registered successfully", shop));

        } catch (Exception e) {
            logger.error("Error registering shop {}: {}", request.getShopDomain(), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to register shop", e.getMessage()));
        }
    }

    /**
     * Request DTO for shop registration
     */
    public static class ShopRegistrationRequest {
        private String shopDomain;
        private String accessToken;
        private String scope;

        public String getShopDomain() {
            return shopDomain;
        }

        public void setShopDomain(String shopDomain) {
            this.shopDomain = shopDomain;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }
    }
}
