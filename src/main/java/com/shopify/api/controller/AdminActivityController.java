package com.shopify.api.controller;

import com.shopify.api.annotation.LogActivity;
import com.shopify.api.model.AdminActivityLog;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.service.AdminActivityLogService;
import com.shopify.api.service.ShopifyShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing admin activity logs.
 * Provides endpoints for viewing, searching, and analyzing admin actions.
 */
@RestController
@RequestMapping("/api/admin/activity")
@CrossOrigin(origins = "*")
public class AdminActivityController {

    @Autowired
    private AdminActivityLogService activityLogService;

    @Autowired
    private ShopifyShopService shopService;

    /**
     * Get paginated activity logs for a shop
     * GET /api/admin/activity/logs?shop=hearnshobbies.myshopify.com&page=0&size=20
     */
    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> getActivityLogs(
            @RequestParam String shop,
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
        Page<AdminActivityLog> logsPage = activityLogService.getActivityLogs(shopEntity, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "logs", logsPage.getContent(),
                "currentPage", logsPage.getNumber(),
                "totalPages", logsPage.getTotalPages(),
                "totalItems", logsPage.getTotalElements(),
                "hasNext", logsPage.hasNext(),
                "hasPrevious", logsPage.hasPrevious()
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Get activity logs by category
     * GET /api/admin/activity/logs/category?shop=hearnshobbies.myshopify.com&category=CONFIG&page=0&size=20
     */
    @GetMapping("/logs/category")
    public ResponseEntity<Map<String, Object>> getActivityLogsByCategory(
            @RequestParam String shop,
            @RequestParam String category,
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
        Page<AdminActivityLog> logsPage = activityLogService.getActivityLogsByCategory(
                shopEntity, category.toUpperCase(), pageable
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "logs", logsPage.getContent(),
                "category", category,
                "currentPage", logsPage.getNumber(),
                "totalPages", logsPage.getTotalPages(),
                "totalItems", logsPage.getTotalElements()
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Get recent activity (last N days)
     * GET /api/admin/activity/recent?shop=hearnshobbies.myshopify.com&days=7
     */
    @GetMapping("/recent")
    public ResponseEntity<Map<String, Object>> getRecentActivity(
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

        List<AdminActivityLog> logs = activityLogService.getRecentActivity(shopEntity, days);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "logs", logs,
                "days", days,
                "count", logs.size()
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Get failed actions for troubleshooting
     * GET /api/admin/activity/failed?shop=hearnshobbies.myshopify.com
     */
    @GetMapping("/failed")
    public ResponseEntity<Map<String, Object>> getFailedActions(
            @RequestParam String shop
    ) {
        ShopifyShop shopEntity = shopService.findByDomain(shop);
        if (shopEntity == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Shop not found: " + shop
            ));
        }

        List<AdminActivityLog> failedLogs = activityLogService.getFailedActions(shopEntity);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "failedActions", failedLogs,
                "count", failedLogs.size()
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Get activity statistics
     * GET /api/admin/activity/statistics?shop=hearnshobbies.myshopify.com&days=30
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getActivityStatistics(
            @RequestParam String shop,
            @RequestParam(defaultValue = "30") int days
    ) {
        ShopifyShop shopEntity = shopService.findByDomain(shop);
        if (shopEntity == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Shop not found: " + shop
            ));
        }

        Map<String, Object> stats = activityLogService.getActivityStatistics(shopEntity, days);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "statistics", stats,
                "period", days + " days"
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Search activity logs
     * GET /api/admin/activity/search?shop=hearnshobbies.myshopify.com&query=AI+model
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchActivityLogs(
            @RequestParam String shop,
            @RequestParam String query
    ) {
        ShopifyShop shopEntity = shopService.findByDomain(shop);
        if (shopEntity == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Shop not found: " + shop
            ));
        }

        List<AdminActivityLog> results = activityLogService.searchActivityLogs(shopEntity, query);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "results", results,
                "query", query,
                "count", results.size()
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Get activity log by ID
     * GET /api/admin/activity/logs/123
     */
    @GetMapping("/logs/{id}")
    public ResponseEntity<Map<String, Object>> getActivityLogById(
            @PathVariable Long id
    ) {
        // This would need a new service method
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Endpoint not yet implemented");

        return ResponseEntity.ok(response);
    }

    /**
     * Anonymize old logs for GDPR compliance
     * POST /api/admin/activity/anonymize?shop=hearnshobbies.myshopify.com&retentionYears=2
     */
    @LogActivity(
            actionType = AdminActivityLog.ACTION_UPDATE,
            actionCategory = AdminActivityLog.CATEGORY_SYSTEM,
            resourceType = "activity_logs",
            descriptionTemplate = "Anonymized logs older than {1} years"
    )
    @PostMapping("/anonymize")
    public ResponseEntity<Map<String, Object>> anonymizeOldLogs(
            @RequestParam String shop,
            @RequestParam(defaultValue = "2") int retentionYears,
            HttpServletRequest request
    ) {
        ShopifyShop shopEntity = shopService.findByDomain(shop);
        if (shopEntity == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Shop not found: " + shop
            ));
        }

        int anonymizedCount = activityLogService.anonymizeOldLogs(retentionYears);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "anonymizedCount", anonymizedCount,
                "retentionYears", retentionYears
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Get available action categories
     * GET /api/admin/activity/categories
     */
    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> getActionCategories() {
        List<String> categories = List.of(
                AdminActivityLog.CATEGORY_CONFIG,
                AdminActivityLog.CATEGORY_PROMPT,
                AdminActivityLog.CATEGORY_TESTING,
                AdminActivityLog.CATEGORY_ANALYTICS,
                AdminActivityLog.CATEGORY_SYSTEM
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("categories", categories));

        return ResponseEntity.ok(response);
    }

    /**
     * Get available action types
     * GET /api/admin/activity/action-types
     */
    @GetMapping("/action-types")
    public ResponseEntity<Map<String, Object>> getActionTypes() {
        List<String> actionTypes = List.of(
                AdminActivityLog.ACTION_CREATE,
                AdminActivityLog.ACTION_UPDATE,
                AdminActivityLog.ACTION_DELETE,
                AdminActivityLog.ACTION_TEST,
                AdminActivityLog.ACTION_ROLLBACK,
                AdminActivityLog.ACTION_VIEW
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("actionTypes", actionTypes));

        return ResponseEntity.ok(response);
    }
}
