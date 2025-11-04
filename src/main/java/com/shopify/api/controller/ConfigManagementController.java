package com.shopify.api.controller;

import com.shopify.api.annotation.LogActivity;
import com.shopify.api.model.AdminActivityLog;
import com.shopify.api.model.ConfigChangeHistory;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.service.ConfigHistoryService;
import com.shopify.api.service.ShopifyShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for configuration management with versioning and rollback.
 * Manages AI model configs, behavior settings, prompts, and full configurations.
 */
@RestController
@RequestMapping("/api/admin/config")
@CrossOrigin(origins = "*")
public class ConfigManagementController {

    @Autowired
    private ConfigHistoryService configHistoryService;

    @Autowired
    private ShopifyShopService shopService;

    /**
     * Save a new configuration version
     * POST /api/admin/config/save
     * Body: {
     *   "shop": "hearnshobbies.myshopify.com",
     *   "configType": "AI_MODEL",
     *   "configSnapshot": {"model": "claude-3-7-sonnet-20250219", "temperature": 0.7},
     *   "changedBy": "admin@example.com",
     *   "changeReason": "Updated to Claude 3.7 Sonnet"
     * }
     */
    @LogActivity(
            actionType = AdminActivityLog.ACTION_CREATE,
            actionCategory = AdminActivityLog.CATEGORY_CONFIG,
            resourceType = "config_version",
            descriptionTemplate = "Saved new configuration version: {1}",
            captureReturnValue = true
    )
    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> saveConfigVersion(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest
    ) {
        String shopDomain = (String) request.get("shop");
        String configType = (String) request.get("configType");
        Map<String, Object> configSnapshot = (Map<String, Object>) request.get("configSnapshot");
        String changedBy = (String) request.get("changedBy");
        String changeReason = (String) request.get("changeReason");

        ShopifyShop shop = shopService.findByDomain(shopDomain);
        if (shop == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Shop not found: " + shopDomain
            ));
        }

        ConfigChangeHistory version = configHistoryService.saveConfigVersion(
                shop,
                configType,
                configSnapshot,
                changedBy,
                changeReason,
                ConfigChangeHistory.CHANGE_MANUAL
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "version", version,
                "message", "Configuration version saved successfully"
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Activate a configuration version
     * POST /api/admin/config/activate/{versionId}
     */
    @LogActivity(
            actionType = AdminActivityLog.ACTION_UPDATE,
            actionCategory = AdminActivityLog.CATEGORY_CONFIG,
            resourceType = "config_version",
            descriptionTemplate = "Activated configuration version {2}"
    )
    @PostMapping("/activate/{versionId}")
    public ResponseEntity<Map<String, Object>> activateVersion(
            @PathVariable Long versionId,
            @RequestParam String shop,
            @RequestParam String configType,
            HttpServletRequest request
    ) {
        ShopifyShop shopEntity = shopService.findByDomain(shop);
        if (shopEntity == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Shop not found: " + shop
            ));
        }

        ConfigChangeHistory activated = configHistoryService.activateVersion(
                shopEntity,
                configType,
                versionId
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "activatedVersion", activated,
                "message", "Configuration version activated"
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Rollback to a previous configuration version
     * POST /api/admin/config/rollback
     * Body: {
     *   "shop": "hearnshobbies.myshopify.com",
     *   "configType": "AI_MODEL",
     *   "targetVersionId": 5,
     *   "rolledBackBy": "admin@example.com",
     *   "rollbackReason": "Performance degradation"
     * }
     */
    @LogActivity(
            actionType = AdminActivityLog.ACTION_ROLLBACK,
            actionCategory = AdminActivityLog.CATEGORY_CONFIG,
            resourceType = "config_version",
            descriptionTemplate = "Rolled back configuration to version {2}",
            captureReturnValue = true
    )
    @PostMapping("/rollback")
    public ResponseEntity<Map<String, Object>> rollbackToVersion(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest
    ) {
        String shopDomain = (String) request.get("shop");
        String configType = (String) request.get("configType");
        Long targetVersionId = Long.valueOf(request.get("targetVersionId").toString());
        String rolledBackBy = (String) request.get("rolledBackBy");
        String rollbackReason = (String) request.get("rollbackReason");

        ShopifyShop shop = shopService.findByDomain(shopDomain);
        if (shop == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Shop not found: " + shopDomain
            ));
        }

        ConfigChangeHistory rolledBack = configHistoryService.rollbackToVersion(
                shop,
                configType,
                targetVersionId,
                rolledBackBy,
                rollbackReason
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "rolledBackVersion", rolledBack,
                "message", "Successfully rolled back to version " + rolledBack.getVersionNumber()
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Get configuration history for a shop
     * GET /api/admin/config/history?shop=hearnshobbies.myshopify.com&configType=AI_MODEL&page=0&size=20
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getConfigHistory(
            @RequestParam String shop,
            @RequestParam String configType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ShopifyShop shopEntity = shopService.findByDomain(shop);
        if (shopEntity == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Shop not found: " + shop
            ));
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<ConfigChangeHistory> historyPage = configHistoryService.getConfigHistory(
                shopEntity, configType, pageable
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "history", historyPage.getContent(),
                "configType", configType,
                "currentPage", historyPage.getNumber(),
                "totalPages", historyPage.getTotalPages(),
                "totalItems", historyPage.getTotalElements()
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Get active configuration version
     * GET /api/admin/config/active?shop=hearnshobbies.myshopify.com&configType=AI_MODEL
     */
    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> getActiveVersion(
            @RequestParam String shop,
            @RequestParam String configType
    ) {
        ShopifyShop shopEntity = shopService.findByDomain(shop);
        if (shopEntity == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Shop not found: " + shop
            ));
        }

        ConfigChangeHistory activeVersion = configHistoryService.getActiveVersion(
                shopEntity, configType
        );

        if (activeVersion == null) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "activeVersion", (Object) null,
                            "message", "No active version found for " + configType
                    )
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "activeVersion", activeVersion,
                "configType", configType
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Get configuration version by ID
     * GET /api/admin/config/versions/{versionId}
     */
    @GetMapping("/versions/{versionId}")
    public ResponseEntity<Map<String, Object>> getVersionById(
            @PathVariable Long versionId
    ) {
        ConfigChangeHistory version = configHistoryService.getVersionById(versionId);

        if (version == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("version", version));

        return ResponseEntity.ok(response);
    }

    /**
     * Compare two configuration versions
     * GET /api/admin/config/compare?version1=5&version2=7
     */
    @GetMapping("/compare")
    public ResponseEntity<Map<String, Object>> compareVersions(
            @RequestParam Long version1,
            @RequestParam Long version2
    ) {
        Map<String, Object> comparison = configHistoryService.compareVersions(version1, version2);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "comparison", comparison,
                "version1Id", version1,
                "version2Id", version2
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Get recent configuration changes
     * GET /api/admin/config/recent?shop=hearnshobbies.myshopify.com&days=7
     */
    @GetMapping("/recent")
    public ResponseEntity<Map<String, Object>> getRecentChanges(
            @RequestParam String shop,
            @RequestParam(defaultValue = "7") int days
    ) {
        ShopifyShop shopEntity = shopService.findByDomain(shop);
        if (shopEntity == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Shop not found: " + shop
            ));
        }

        List<ConfigChangeHistory> recentChanges = configHistoryService.getRecentChanges(
                shopEntity, days
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "recentChanges", recentChanges,
                "days", days,
                "count", recentChanges.size()
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Get available configuration types
     * GET /api/admin/config/types
     */
    @GetMapping("/types")
    public ResponseEntity<Map<String, Object>> getConfigTypes() {
        List<String> configTypes = List.of(
                ConfigChangeHistory.TYPE_AI_MODEL,
                ConfigChangeHistory.TYPE_BEHAVIOR,
                ConfigChangeHistory.TYPE_PROMPT,
                ConfigChangeHistory.TYPE_ANALYTICS,
                ConfigChangeHistory.TYPE_FULL_CONFIG
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("configTypes", configTypes));

        return ResponseEntity.ok(response);
    }

    /**
     * Mark version as non-rollbackable
     * POST /api/admin/config/versions/{versionId}/disable-rollback
     */
    @LogActivity(
            actionType = AdminActivityLog.ACTION_UPDATE,
            actionCategory = AdminActivityLog.CATEGORY_CONFIG,
            resourceType = "config_version",
            descriptionTemplate = "Disabled rollback for version {2}"
    )
    @PostMapping("/versions/{versionId}/disable-rollback")
    public ResponseEntity<Map<String, Object>> disableRollback(
            @PathVariable Long versionId,
            @RequestParam String reason,
            HttpServletRequest request
    ) {
        ConfigChangeHistory version = configHistoryService.getVersionById(versionId);
        if (version == null) {
            return ResponseEntity.notFound().build();
        }

        version.setCanRollback(false);
        version.setRollbackNotes(reason);
        configHistoryService.saveVersion(version);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "version", version,
                "message", "Rollback disabled for version " + versionId
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Cleanup old configuration versions
     * POST /api/admin/config/cleanup?shop=hearnshobbies.myshopify.com&configType=AI_MODEL&keepCount=10
     */
    @LogActivity(
            actionType = AdminActivityLog.ACTION_DELETE,
            actionCategory = AdminActivityLog.CATEGORY_SYSTEM,
            resourceType = "config_versions",
            descriptionTemplate = "Cleaned up old config versions, kept {2}"
    )
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupOldVersions(
            @RequestParam String shop,
            @RequestParam String configType,
            @RequestParam(defaultValue = "10") int keepCount,
            HttpServletRequest request
    ) {
        ShopifyShop shopEntity = shopService.findByDomain(shop);
        if (shopEntity == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Shop not found: " + shop
            ));
        }

        int deletedCount = configHistoryService.cleanupOldVersions(
                shopEntity, configType, keepCount
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "deletedCount", deletedCount,
                "keptCount", keepCount,
                "message", "Cleaned up " + deletedCount + " old versions"
        ));

        return ResponseEntity.ok(response);
    }
}
