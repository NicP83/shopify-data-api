package com.shopify.api.repository;

import com.shopify.api.model.ChatbotConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ChatbotConfigEntity
 * Manages persistent chatbot configurations
 */
@Repository
public interface ChatbotConfigRepository extends JpaRepository<ChatbotConfigEntity, Long> {

    /**
     * Find configuration for a specific shop
     */
    @Query("SELECT c FROM ChatbotConfigEntity c WHERE c.shop.id = :shopId AND c.isActive = true")
    Optional<ChatbotConfigEntity> findByShopId(Long shopId);

    /**
     * Find the global default configuration (shop_id is NULL and not a named
     * persona). Excludes persona rows (slug IS NOT NULL) so the storefront
     * never picks a persona up as its global default.
     */
    @Query("SELECT c FROM ChatbotConfigEntity c WHERE c.shop IS NULL AND c.slug IS NULL AND c.isActive = true")
    Optional<ChatbotConfigEntity> findGlobalConfig();

    /**
     * Find a named persona profile by slug.
     */
    @Query("SELECT c FROM ChatbotConfigEntity c WHERE c.slug = :slug AND c.isActive = true")
    Optional<ChatbotConfigEntity> findBySlug(String slug);

    /**
     * List all named persona profiles (slug IS NOT NULL).
     */
    @Query("SELECT c FROM ChatbotConfigEntity c WHERE c.slug IS NOT NULL AND c.isActive = true ORDER BY c.displayName")
    List<ChatbotConfigEntity> findAllProfiles();

    /**
     * Find active configuration by shop ID
     */
    @Query("SELECT c FROM ChatbotConfigEntity c WHERE c.shop.id = :shopId AND c.isActive = true")
    Optional<ChatbotConfigEntity> findActiveByShopId(Long shopId);

    /**
     * Check if a configuration exists for a shop
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM ChatbotConfigEntity c " +
           "WHERE c.shop.id = :shopId AND c.isActive = true")
    boolean existsByShopId(Long shopId);

    /**
     * Check if a global configuration exists
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM ChatbotConfigEntity c " +
           "WHERE c.shop IS NULL AND c.isActive = true")
    boolean existsGlobalConfig();
}
