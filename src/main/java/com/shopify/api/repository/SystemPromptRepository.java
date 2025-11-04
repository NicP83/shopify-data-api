package com.shopify.api.repository;

import com.shopify.api.model.SystemPrompt;
import com.shopify.api.model.ShopifyShop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for SystemPrompt entities
 */
@Repository
public interface SystemPromptRepository extends JpaRepository<SystemPrompt, Long> {

    /**
     * Find active prompt by name for a specific shop
     * Falls back to global prompt if shop-specific doesn't exist
     */
    @Query("""
        SELECT sp FROM SystemPrompt sp
        WHERE sp.promptName = :promptName
        AND sp.isActive = true
        AND (sp.shop = :shop OR sp.shop IS NULL)
        ORDER BY sp.shop NULLS LAST, sp.version DESC
        LIMIT 1
        """)
    Optional<SystemPrompt> findActivePromptForShop(
        @Param("promptName") String promptName,
        @Param("shop") ShopifyShop shop
    );

    /**
     * Find active prompt by type for a specific shop
     */
    @Query("""
        SELECT sp FROM SystemPrompt sp
        WHERE sp.promptType = :promptType
        AND sp.isActive = true
        AND (sp.shop = :shop OR sp.shop IS NULL)
        ORDER BY sp.shop NULLS LAST, sp.version DESC
        LIMIT 1
        """)
    Optional<SystemPrompt> findActivePromptByTypeForShop(
        @Param("promptType") String promptType,
        @Param("shop") ShopifyShop shop
    );

    /**
     * Find all active prompts for a shop (includes global)
     */
    @Query("""
        SELECT sp FROM SystemPrompt sp
        WHERE sp.isActive = true
        AND (sp.shop = :shop OR sp.shop IS NULL)
        ORDER BY sp.promptType, sp.shop NULLS LAST, sp.version DESC
        """)
    List<SystemPrompt> findAllActivePromptsForShop(@Param("shop") ShopifyShop shop);

    /**
     * Find shop-specific prompts only
     */
    @Query("""
        SELECT sp FROM SystemPrompt sp
        WHERE sp.shop = :shop
        AND sp.isActive = true
        ORDER BY sp.promptType, sp.version DESC
        """)
    List<SystemPrompt> findShopSpecificPrompts(@Param("shop") ShopifyShop shop);

    /**
     * Find all global prompts
     */
    @Query("""
        SELECT sp FROM SystemPrompt sp
        WHERE sp.shop IS NULL
        AND sp.isActive = true
        ORDER BY sp.promptType, sp.version DESC
        """)
    List<SystemPrompt> findAllGlobalPrompts();

    /**
     * Find all versions of a prompt
     */
    @Query("""
        SELECT sp FROM SystemPrompt sp
        WHERE sp.promptName = :promptName
        AND (sp.shop = :shop OR (sp.shop IS NULL AND :shop IS NULL))
        ORDER BY sp.version DESC
        """)
    List<SystemPrompt> findAllVersions(
        @Param("promptName") String promptName,
        @Param("shop") ShopifyShop shop
    );

    /**
     * Find prompt by name, shop, and version
     */
    Optional<SystemPrompt> findByPromptNameAndShopAndVersion(
        String promptName,
        ShopifyShop shop,
        Integer version
    );

    /**
     * Find latest version number for a prompt
     */
    @Query("""
        SELECT COALESCE(MAX(sp.version), 0)
        FROM SystemPrompt sp
        WHERE sp.promptName = :promptName
        AND (sp.shop = :shop OR (sp.shop IS NULL AND :shop IS NULL))
        """)
    Integer findLatestVersion(
        @Param("promptName") String promptName,
        @Param("shop") ShopifyShop shop
    );

    /**
     * Find prompts by type
     */
    List<SystemPrompt> findByPromptTypeAndIsActive(String promptType, Boolean isActive);

    /**
     * Count active prompts for a shop
     */
    @Query("""
        SELECT COUNT(sp)
        FROM SystemPrompt sp
        WHERE sp.shop = :shop
        AND sp.isActive = true
        """)
    long countActivePromptsForShop(@Param("shop") ShopifyShop shop);

    /**
     * Find top performing prompts by success rate
     */
    @Query("""
        SELECT sp FROM SystemPrompt sp
        WHERE sp.isActive = true
        AND sp.usageCount > 10
        ORDER BY sp.successRate DESC, sp.usageCount DESC
        LIMIT 10
        """)
    List<SystemPrompt> findTopPerformingPrompts();
}
