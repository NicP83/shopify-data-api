package com.shopify.api.service;

import com.shopify.api.model.ShopifyShop;
import com.shopify.api.repository.ShopifyShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing Shopify shops
 */
@Service
public class ShopifyShopService {

    private static final Logger logger = LoggerFactory.getLogger(ShopifyShopService.class);

    private final ShopifyShopRepository shopifyShopRepository;
    private final ShopifyOAuthService shopifyOAuthService;

    @Autowired
    public ShopifyShopService(ShopifyShopRepository shopifyShopRepository,
                              ShopifyOAuthService shopifyOAuthService) {
        this.shopifyShopRepository = shopifyShopRepository;
        this.shopifyOAuthService = shopifyOAuthService;
    }

    /**
     * Save or update shop after OAuth installation
     */
    @Transactional
    public ShopifyShop saveShop(String shopDomain, String accessToken, String scope) {
        logger.info("Saving shop: {}", shopDomain);

        // Fetch shop details from Shopify
        Map<String, Object> shopDetails = shopifyOAuthService.fetchShopDetails(shopDomain, accessToken);

        // Check if shop already exists
        Optional<ShopifyShop> existingShop = shopifyShopRepository.findByShopDomain(shopDomain);

        ShopifyShop shop;
        if (existingShop.isPresent()) {
            // Update existing shop
            shop = existingShop.get();
            shop.setAccessToken(accessToken);
            shop.setScope(scope);
            shop.setIsActive(true);
            shop.setUninstalledAt(null); // Clear uninstall timestamp
            logger.info("Updating existing shop: {}", shopDomain);
        } else {
            // Create new shop
            shop = ShopifyShop.builder()
                .shopDomain(shopDomain)
                .accessToken(accessToken)
                .scope(scope)
                .isActive(true)
                .installedAt(LocalDateTime.now())
                .build();
            logger.info("Creating new shop: {}", shopDomain);
        }

        // Update shop details from Shopify API
        if (!shopDetails.isEmpty()) {
            shop.setShopName((String) shopDetails.get("name"));
            shop.setShopEmail((String) shopDetails.get("email"));
            shop.setShopOwner((String) shopDetails.get("shop_owner"));
            shop.setPlanName((String) shopDetails.get("plan_name"));
            shop.setCurrency((String) shopDetails.get("currency"));
            shop.setTimezone((String) shopDetails.get("timezone"));
        }

        return shopifyShopRepository.save(shop);
    }

    /**
     * Get shop by domain
     */
    public ShopifyShop getShop(String shopDomain) {
        return shopifyShopRepository.findByShopDomain(shopDomain)
            .orElseThrow(() -> new RuntimeException("Shop not found: " + shopDomain));
    }

    /**
     * Get shop by domain (optional)
     */
    public Optional<ShopifyShop> getShopOptional(String shopDomain) {
        return shopifyShopRepository.findByShopDomain(shopDomain);
    }

    /**
     * Find shop by domain (returns null if not found)
     * Used by admin controllers
     */
    public ShopifyShop findByDomain(String shopDomain) {
        return shopifyShopRepository.findByShopDomain(shopDomain).orElse(null);
    }

    /**
     * Check if shop is installed and active
     */
    public boolean isShopActive(String shopDomain) {
        return shopifyShopRepository.existsActiveShop(shopDomain);
    }

    /**
     * Get all active shops
     */
    public List<ShopifyShop> getAllActiveShops() {
        return shopifyShopRepository.findAllActive();
    }

    /**
     * Update shop
     */
    @Transactional
    public ShopifyShop updateShop(ShopifyShop shop) {
        return shopifyShopRepository.save(shop);
    }

    /**
     * Mark shop as uninstalled
     */
    @Transactional
    public void uninstallShop(String shopDomain) {
        Optional<ShopifyShop> shopOpt = shopifyShopRepository.findByShopDomain(shopDomain);

        if (shopOpt.isPresent()) {
            ShopifyShop shop = shopOpt.get();
            shop.setIsActive(false);
            shop.setUninstalledAt(LocalDateTime.now());
            shopifyShopRepository.save(shop);
            logger.info("Shop uninstalled: {}", shopDomain);
        }
    }
}
