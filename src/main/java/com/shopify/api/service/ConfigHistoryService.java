package com.shopify.api.service;

import com.shopify.api.model.AdminActivityLog;
import com.shopify.api.model.ConfigChangeHistory;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.repository.ConfigChangeHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing configuration change history.
 * Provides versioning and rollback functionality.
 */
@Service
public class ConfigHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigHistoryService.class);

    @Autowired
    private ConfigChangeHistoryRepository configHistoryRepository;

    @Autowired
    private AdminActivityLogService activityLogService;

    /**
     * Save a new configuration version
     */
    @Transactional
    public ConfigChangeHistory saveConfigVersion(
            ShopifyShop shop,
            String configType,
            Map<String, Object> configSnapshot,
            String changedBy,
            String changeReason,
            String changeType
    ) {
        // Get next version number
        Integer nextVersion = configHistoryRepository.findMaxVersionNumber(shop, configType)
                .orElse(0) + 1;

        // Deactivate current active version
        configHistoryRepository.deactivateAllByShopAndConfigType(
                shop,
                configType,
                ZonedDateTime.now()
        );

        // Create new version
        ConfigChangeHistory history = new ConfigChangeHistory(
                shop,
                configType,
                configSnapshot,
                changeType,
                changedBy,
                nextVersion
        );

        history.setChangeReason(changeReason);
        history.activate();

        ConfigChangeHistory saved = configHistoryRepository.save(history);

        logger.info("Saved config version {} for shop {} - type: {}",
                nextVersion, shop.getShopDomain(), configType);

        return saved;
    }

    /**
     * Get active configuration for a shop and type
     */
    @Transactional(readOnly = true)
    public Optional<ConfigChangeHistory> getActiveConfig(ShopifyShop shop, String configType) {
        return configHistoryRepository.findByShopAndConfigTypeAndIsActiveTrue(shop, configType);
    }

    /**
     * Get all configuration versions for a shop and type
     */
    @Transactional(readOnly = true)
    public List<ConfigChangeHistory> getConfigVersions(ShopifyShop shop, String configType) {
        return configHistoryRepository.findByShopAndConfigTypeOrderByVersionNumberDesc(
                shop,
                configType
        );
    }

    /**
     * Rollback to a previous configuration version
     */
    @Transactional
    public ConfigChangeHistory rollbackToVersion(
            ShopifyShop shop,
            String configType,
            Long historyId,
            String rolledBackBy,
            String rollbackReason
    ) {
        // Find the target version
        ConfigChangeHistory targetVersion = configHistoryRepository.findById(historyId)
                .orElseThrow(() -> new IllegalArgumentException("Config version not found: " + historyId));

        // Verify it's rollback-eligible
        if (!targetVersion.canRollbackTo()) {
            throw new IllegalStateException("Cannot rollback to this version");
        }

        // Get current active version for comparison
        Optional<ConfigChangeHistory> currentVersion = getActiveConfig(shop, configType);

        // Deactivate current version
        configHistoryRepository.deactivateAllByShopAndConfigType(
                shop,
                configType,
                ZonedDateTime.now()
        );

        // Create new version with rolled-back config
        Integer nextVersion = configHistoryRepository.findMaxVersionNumber(shop, configType)
                .orElse(0) + 1;

        ConfigChangeHistory rolledBackVersion = new ConfigChangeHistory(
                shop,
                configType,
                targetVersion.getConfigSnapshot(),
                ConfigChangeHistory.CHANGE_ROLLBACK,
                rolledBackBy,
                nextVersion
        );

        rolledBackVersion.setChangeReason(
                "Rolled back to version " + targetVersion.getVersionNumber() +
                        (rollbackReason != null ? ": " + rollbackReason : "")
        );

        // Copy performance data from target version
        if (targetVersion.getPerformanceAfter() != null) {
            rolledBackVersion.setPerformanceBefore(
                    currentVersion.map(ConfigChangeHistory::getPerformanceAfter).orElse(null)
            );
        }

        rolledBackVersion.activate();

        ConfigChangeHistory saved = configHistoryRepository.save(rolledBackVersion);

        logger.info("Rolled back config to version {} for shop {} - type: {}",
                targetVersion.getVersionNumber(), shop.getShopDomain(), configType);

        return saved;
    }

    /**
     * Update performance metrics for a configuration
     */
    @Transactional
    public void updatePerformanceMetrics(
            Long historyId,
            Map<String, Object> performanceData,
            boolean isBefore
    ) {
        ConfigChangeHistory history = configHistoryRepository.findById(historyId)
                .orElseThrow(() -> new IllegalArgumentException("Config version not found"));

        if (isBefore) {
            history.setPerformanceBefore(performanceData);
        } else {
            history.setPerformanceAfter(performanceData);
        }

        configHistoryRepository.save(history);

        logger.debug("Updated {} performance metrics for config version {}",
                isBefore ? "before" : "after", historyId);
    }

    /**
     * Get configuration history with performance data
     */
    @Transactional(readOnly = true)
    public List<ConfigChangeHistory> getConfigsWithPerformance(ShopifyShop shop, int limit) {
        return configHistoryRepository.findConfigsWithPerformanceData(
                shop,
                org.springframework.data.domain.Pageable.ofSize(limit)
        );
    }

    /**
     * Compare two configuration versions
     */
    @Transactional(readOnly = true)
    public Map<String, Object> compareVersions(Long version1Id, Long version2Id) {
        ConfigChangeHistory v1 = configHistoryRepository.findById(version1Id)
                .orElseThrow(() -> new IllegalArgumentException("Version 1 not found"));
        ConfigChangeHistory v2 = configHistoryRepository.findById(version2Id)
                .orElseThrow(() -> new IllegalArgumentException("Version 2 not found"));

        Map<String, Object> comparison = new HashMap<>();

        comparison.put("version1", Map.of(
                "versionNumber", v1.getVersionNumber(),
                "createdAt", v1.getCreatedAt(),
                "changedBy", v1.getChangedBy(),
                "isActive", v1.getIsActive(),
                "config", v1.getConfigSnapshot()
        ));

        comparison.put("version2", Map.of(
                "versionNumber", v2.getVersionNumber(),
                "createdAt", v2.getCreatedAt(),
                "changedBy", v2.getChangedBy(),
                "isActive", v2.getIsActive(),
                "config", v2.getConfigSnapshot()
        ));

        // Calculate differences
        Map<String, Object> differences = new HashMap<>();
        compareConfigs(v1.getConfigSnapshot(), v2.getConfigSnapshot(), differences);
        comparison.put("differences", differences);

        // Performance comparison
        if (v1.getPerformanceAfter() != null && v2.getPerformanceAfter() != null) {
            comparison.put("performanceComparison", Map.of(
                    "version1Performance", v1.getPerformanceAfter(),
                    "version2Performance", v2.getPerformanceAfter(),
                    "improvement", calculatePerformanceImprovement(
                            v1.getPerformanceAfter(),
                            v2.getPerformanceAfter()
                    )
            ));
        }

        return comparison;
    }

    /**
     * Get configuration change statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getChangeStatistics(ShopifyShop shop, int days) {
        ZonedDateTime since = ZonedDateTime.now().minusDays(days);

        Map<String, Object> stats = new HashMap<>();

        // Count by type
        List<Object[]> typeCounts = configHistoryRepository.countChangesByType(shop, since);
        Map<String, Long> typeMap = new HashMap<>();
        for (Object[] row : typeCounts) {
            typeMap.put((String) row[0], (Long) row[1]);
        }
        stats.put("changesByType", typeMap);

        // Count rollbacks
        Long rollbackCount = configHistoryRepository.countRollbacks(shop, since);
        stats.put("rollbackCount", rollbackCount);

        // Recent changes
        List<ConfigChangeHistory> recentChanges = configHistoryRepository.findRecentChanges(shop, since);
        stats.put("recentChangesCount", recentChanges.size());

        // Active configurations
        List<ConfigChangeHistory> activeConfigs = configHistoryRepository.findByShopAndIsActiveTrueOrderByConfigType(shop);
        stats.put("activeConfigurationsCount", activeConfigs.size());

        return stats;
    }

    /**
     * Clean up old configuration versions
     */
    @Transactional
    public int cleanupOldVersions(ShopifyShop shop, int keepVersions) {
        List<String> configTypes = List.of(
                ConfigChangeHistory.TYPE_AI_MODEL,
                ConfigChangeHistory.TYPE_BEHAVIOR,
                ConfigChangeHistory.TYPE_PROMPT,
                ConfigChangeHistory.TYPE_ANALYTICS,
                ConfigChangeHistory.TYPE_FULL_CONFIG
        );

        int totalDeleted = 0;
        for (String configType : configTypes) {
            try {
                configHistoryRepository.deleteOldVersions(shop, configType, keepVersions);
                totalDeleted++;
            } catch (Exception e) {
                logger.error("Failed to cleanup old versions for type: {}", configType, e);
            }
        }

        logger.info("Cleaned up old config versions for shop {}, kept last {} versions per type",
                shop.getShopDomain(), keepVersions);

        return totalDeleted;
    }

    /**
     * Get rollback-eligible versions
     */
    @Transactional(readOnly = true)
    public List<ConfigChangeHistory> getRollbackOptions(ShopifyShop shop, String configType) {
        return configHistoryRepository.findRollbackEligible(shop, configType);
    }

    /**
     * Helper method to compare two config maps
     */
    private void compareConfigs(
            Map<String, Object> config1,
            Map<String, Object> config2,
            Map<String, Object> differences
    ) {
        // Find keys in config1
        for (String key : config1.keySet()) {
            Object value1 = config1.get(key);
            Object value2 = config2.get(key);

            if (value2 == null) {
                differences.put(key, Map.of("removed", value1));
            } else if (!value1.equals(value2)) {
                differences.put(key, Map.of(
                        "old", value1,
                        "new", value2
                ));
            }
        }

        // Find keys only in config2
        for (String key : config2.keySet()) {
            if (!config1.containsKey(key)) {
                differences.put(key, Map.of("added", config2.get(key)));
            }
        }
    }

    /**
     * Calculate performance improvement between two performance snapshots
     */
    private Map<String, Object> calculatePerformanceImprovement(
            Map<String, Object> before,
            Map<String, Object> after
    ) {
        Map<String, Object> improvements = new HashMap<>();

        for (String key : before.keySet()) {
            if (after.containsKey(key)) {
                Object beforeVal = before.get(key);
                Object afterVal = after.get(key);

                if (beforeVal instanceof Number && afterVal instanceof Number) {
                    double beforeNum = ((Number) beforeVal).doubleValue();
                    double afterNum = ((Number) afterVal).doubleValue();
                    double percentChange = ((afterNum - beforeNum) / beforeNum) * 100;

                    improvements.put(key, Map.of(
                            "before", beforeNum,
                            "after", afterNum,
                            "percentChange", percentChange
                    ));
                }
            }
        }

        return improvements;
    }
}
