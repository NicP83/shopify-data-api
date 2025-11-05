package com.shopify.api.repository;

import com.shopify.api.model.ChatbotConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
     * Find the global default configuration (shop_id is NULL)
     */
    @Query("SELECT c FROM ChatbotConfigEntity c WHERE c.shop IS NULL AND c.isActive = true")
    Optional<ChatbotConfigEntity> findGlobalConfig();

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
