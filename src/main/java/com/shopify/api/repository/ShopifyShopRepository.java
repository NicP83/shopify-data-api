package com.shopify.api.repository;

import com.shopify.api.model.ShopifyShop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ShopifyShop entity
 */
@Repository
public interface ShopifyShopRepository extends JpaRepository<ShopifyShop, Long> {

    /**
     * Find shop by domain
     */
    Optional<ShopifyShop> findByShopDomain(String shopDomain);

    /**
     * Find all active shops
     */
    @Query("SELECT s FROM ShopifyShop s WHERE s.isActive = true AND s.uninstalledAt IS NULL")
    List<ShopifyShop> findAllActive();

    /**
     * Check if shop exists and is active
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM ShopifyShop s " +
           "WHERE s.shopDomain = :shopDomain AND s.isActive = true AND s.uninstalledAt IS NULL")
    boolean existsActiveShop(String shopDomain);

    /**
     * Find shops with AI enabled
     */
    @Query("SELECT s FROM ShopifyShop s WHERE s.isActive = true AND s.aiEnabled = true")
    List<ShopifyShop> findAllWithAiEnabled();
}
