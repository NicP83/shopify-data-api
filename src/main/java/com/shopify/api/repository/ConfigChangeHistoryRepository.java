package com.shopify.api.repository;

import com.shopify.api.model.ConfigChangeHistory;
import com.shopify.api.model.ShopifyShop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ConfigChangeHistory entity.
 * Provides database access for configuration versioning and rollback.
 */
@Repository
public interface ConfigChangeHistoryRepository extends JpaRepository<ConfigChangeHistory, Long> {

    /**
     * Find all configuration history for a shop
     */
    Page<ConfigChangeHistory> findByShopOrderByCreatedAtDesc(ShopifyShop shop, Pageable pageable);

    /**
     * Find configuration history by shop and config type
     */
    List<ConfigChangeHistory> findByShopAndConfigTypeOrderByVersionNumberDesc(
            ShopifyShop shop,
            String configType
    );

    /**
     * Find the active configuration for a shop and config type
     */
    Optional<ConfigChangeHistory> findByShopAndConfigTypeAndIsActiveTrue(
            ShopifyShop shop,
            String configType
    );

    /**
     * Find all active configurations for a shop
     */
    List<ConfigChangeHistory> findByShopAndIsActiveTrueOrderByConfigType(ShopifyShop shop);

    /**
     * Find configuration history by change type
     */
    List<ConfigChangeHistory> findByShopAndChangeTypeOrderByCreatedAtDesc(
            ShopifyShop shop,
            String changeType
    );

    /**
     * Find configurations by user who changed them
     */
    List<ConfigChangeHistory> findByChangedByOrderByCreatedAtDesc(String changedBy);

    /**
     * Find rollback-eligible configurations
     */
    @Query("SELECT c FROM ConfigChangeHistory c WHERE c.shop = :shop " +
           "AND c.configType = :configType AND c.canRollback = true " +
           "AND c.isActive = false ORDER BY c.versionNumber DESC")
    List<ConfigChangeHistory> findRollbackEligible(
            @Param("shop") ShopifyShop shop,
            @Param("configType") String configType
    );

    /**
     * Get latest version number for a config type
     */
    @Query("SELECT MAX(c.versionNumber) FROM ConfigChangeHistory c " +
           "WHERE c.shop = :shop AND c.configType = :configType")
    Optional<Integer> findMaxVersionNumber(
            @Param("shop") ShopifyShop shop,
            @Param("configType") String configType
    );

    /**
     * Deactivate all configurations of a type for a shop
     */
    @Modifying
    @Query("UPDATE ConfigChangeHistory c SET c.isActive = false, c.deactivatedAt = :deactivatedAt " +
           "WHERE c.shop = :shop AND c.configType = :configType AND c.isActive = true")
    void deactivateAllByShopAndConfigType(
            @Param("shop") ShopifyShop shop,
            @Param("configType") String configType,
            @Param("deactivatedAt") ZonedDateTime deactivatedAt
    );

    /**
     * Count configuration changes by type for a shop
     */
    @Query("SELECT c.configType, COUNT(c) FROM ConfigChangeHistory c " +
           "WHERE c.shop = :shop AND c.createdAt >= :since " +
           "GROUP BY c.configType")
    List<Object[]> countChangesByType(
            @Param("shop") ShopifyShop shop,
            @Param("since") ZonedDateTime since
    );

    /**
     * Find recent configuration changes (last N days)
     */
    @Query("SELECT c FROM ConfigChangeHistory c WHERE c.shop = :shop " +
           "AND c.createdAt >= :since ORDER BY c.createdAt DESC")
    List<ConfigChangeHistory> findRecentChanges(
            @Param("shop") ShopifyShop shop,
            @Param("since") ZonedDateTime since
    );

    /**
     * Find configurations by version range
     */
    @Query("SELECT c FROM ConfigChangeHistory c WHERE c.shop = :shop " +
           "AND c.configType = :configType " +
           "AND c.versionNumber BETWEEN :minVersion AND :maxVersion " +
           "ORDER BY c.versionNumber DESC")
    List<ConfigChangeHistory> findByVersionRange(
            @Param("shop") ShopifyShop shop,
            @Param("configType") String configType,
            @Param("minVersion") Integer minVersion,
            @Param("maxVersion") Integer maxVersion
    );

    /**
     * Count rollbacks for a shop
     */
    @Query("SELECT COUNT(c) FROM ConfigChangeHistory c " +
           "WHERE c.shop = :shop AND c.changeType = 'ROLLBACK' AND c.createdAt >= :since")
    Long countRollbacks(
            @Param("shop") ShopifyShop shop,
            @Param("since") ZonedDateTime since
    );

    /**
     * Find configurations with performance data
     */
    @Query("SELECT c FROM ConfigChangeHistory c WHERE c.shop = :shop " +
           "AND c.performanceBefore IS NOT NULL AND c.performanceAfter IS NOT NULL " +
           "ORDER BY c.createdAt DESC")
    List<ConfigChangeHistory> findConfigsWithPerformanceData(
            @Param("shop") ShopifyShop shop,
            Pageable pageable
    );

    /**
     * Delete old non-active configurations (keep last N versions)
     */
    @Modifying
    @Query("DELETE FROM ConfigChangeHistory c WHERE c.id IN (" +
           "  SELECT ch.id FROM ConfigChangeHistory ch " +
           "  WHERE ch.shop = :shop AND ch.configType = :configType " +
           "  AND ch.isActive = false " +
           "  AND ch.versionNumber < (" +
           "    SELECT MAX(ch2.versionNumber) - :keepVersions FROM ConfigChangeHistory ch2 " +
           "    WHERE ch2.shop = :shop AND ch2.configType = :configType" +
           "  )" +
           ")")
    void deleteOldVersions(
            @Param("shop") ShopifyShop shop,
            @Param("configType") String configType,
            @Param("keepVersions") Integer keepVersions
    );
}
