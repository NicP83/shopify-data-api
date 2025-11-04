package com.shopify.api.service;

import com.shopify.api.model.AdminActivityLog;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.repository.AdminActivityLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing admin activity logs.
 * Provides comprehensive audit trail functionality.
 */
@Service
public class AdminActivityLogService {

    private static final Logger logger = LoggerFactory.getLogger(AdminActivityLogService.class);

    @Autowired
    private AdminActivityLogRepository activityLogRepository;

    /**
     * Log an admin activity
     * Uses REQUIRES_NEW to ensure logging happens even if main transaction fails
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logActivity(
            ShopifyShop shop,
            String userEmail,
            String actionType,
            String actionCategory,
            String resourceType,
            String resourceId,
            Map<String, Object> previousValue,
            Map<String, Object> newValue,
            String changeSummary,
            HttpServletRequest request
    ) {
        try {
            AdminActivityLog log = new AdminActivityLog(
                    shop,
                    userEmail,
                    actionType,
                    actionCategory,
                    resourceType,
                    resourceId
            );

            log.setPreviousValue(previousValue);
            log.setNewValue(newValue);
            log.setChangeSummary(changeSummary);

            if (request != null) {
                log.setUserIp(extractIpAddress(request));
                log.setUserAgent(request.getHeader("User-Agent"));
            }

            activityLogRepository.save(log);

            logger.debug("Activity logged: {} - {} - {} by {}",
                    actionCategory, actionType, resourceType, userEmail);

        } catch (Exception e) {
            logger.error("Failed to log activity: {} - {} - {}",
                    actionCategory, actionType, resourceType, e);
            // Don't throw - logging failure shouldn't break the main operation
        }
    }

    /**
     * Log a successful activity with duration
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(
            ShopifyShop shop,
            String userEmail,
            String actionType,
            String actionCategory,
            String resourceType,
            String resourceId,
            String changeSummary,
            long startTimeMs,
            HttpServletRequest request
    ) {
        AdminActivityLog log = new AdminActivityLog(
                shop,
                userEmail,
                actionType,
                actionCategory,
                resourceType,
                resourceId
        );

        log.setChangeSummary(changeSummary);
        log.setDuration(startTimeMs);
        log.setSuccess(true);

        if (request != null) {
            log.setUserIp(extractIpAddress(request));
            log.setUserAgent(request.getHeader("User-Agent"));
        }

        activityLogRepository.save(log);
    }

    /**
     * Log a failed activity with error message
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(
            ShopifyShop shop,
            String userEmail,
            String actionType,
            String actionCategory,
            String resourceType,
            String resourceId,
            String errorMessage,
            HttpServletRequest request
    ) {
        AdminActivityLog log = new AdminActivityLog(
                shop,
                userEmail,
                actionType,
                actionCategory,
                resourceType,
                resourceId
        );

        log.markFailed(errorMessage);

        if (request != null) {
            log.setUserIp(extractIpAddress(request));
            log.setUserAgent(request.getHeader("User-Agent"));
        }

        activityLogRepository.save(log);

        logger.warn("Failed activity logged: {} - {} - {} by {}: {}",
                actionCategory, actionType, resourceType, userEmail, errorMessage);
    }

    /**
     * Log configuration change with before/after values
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logConfigChange(
            ShopifyShop shop,
            String userEmail,
            String configType,
            Map<String, Object> previousConfig,
            Map<String, Object> newConfig,
            String changeSummary,
            HttpServletRequest request
    ) {
        logActivity(
                shop,
                userEmail,
                AdminActivityLog.ACTION_UPDATE,
                AdminActivityLog.CATEGORY_CONFIG,
                configType,
                null,
                previousConfig,
                newConfig,
                changeSummary,
                request
        );
    }

    /**
     * Log prompt change
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logPromptChange(
            ShopifyShop shop,
            String userEmail,
            String actionType,
            Long promptId,
            String promptName,
            String changeSummary,
            HttpServletRequest request
    ) {
        logActivity(
                shop,
                userEmail,
                actionType,
                AdminActivityLog.CATEGORY_PROMPT,
                "system_prompt",
                promptId != null ? promptId.toString() : null,
                null,
                null,
                changeSummary,
                request
        );
    }

    /**
     * Log prompt test activity
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logPromptTest(
            ShopifyShop shop,
            String userEmail,
            String testQuery,
            Long promptId,
            boolean success,
            HttpServletRequest request
    ) {
        AdminActivityLog log = new AdminActivityLog(
                shop,
                userEmail,
                AdminActivityLog.ACTION_TEST,
                AdminActivityLog.CATEGORY_TESTING,
                "prompt_test",
                promptId != null ? promptId.toString() : "unsaved"
        );

        log.setChangeSummary("Tested prompt with query: " + testQuery);
        log.setSuccess(success);

        if (request != null) {
            log.setUserIp(extractIpAddress(request));
            log.setUserAgent(request.getHeader("User-Agent"));
        }

        activityLogRepository.save(log);
    }

    /**
     * Get activity logs for a shop
     */
    @Transactional(readOnly = true)
    public Page<AdminActivityLog> getActivityLogs(ShopifyShop shop, Pageable pageable) {
        return activityLogRepository.findByShopOrderByCreatedAtDesc(shop, pageable);
    }

    /**
     * Get activity logs by category
     */
    @Transactional(readOnly = true)
    public Page<AdminActivityLog> getActivityLogsByCategory(
            ShopifyShop shop,
            String category,
            Pageable pageable
    ) {
        return activityLogRepository.findByShopAndActionCategoryOrderByCreatedAtDesc(
                shop, category, pageable
        );
    }

    /**
     * Get recent activity (last N days)
     */
    @Transactional(readOnly = true)
    public List<AdminActivityLog> getRecentActivity(ShopifyShop shop, int days) {
        ZonedDateTime since = ZonedDateTime.now().minusDays(days);
        return activityLogRepository.findRecentActivity(shop, since);
    }

    /**
     * Get failed actions for a shop
     */
    @Transactional(readOnly = true)
    public List<AdminActivityLog> getFailedActions(ShopifyShop shop) {
        return activityLogRepository.findByShopAndSuccessFalseOrderByCreatedAtDesc(shop);
    }

    /**
     * Get activity statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getActivityStatistics(ShopifyShop shop, int days) {
        ZonedDateTime since = ZonedDateTime.now().minusDays(days);

        Map<String, Object> stats = new HashMap<>();

        // Count by category
        List<Object[]> categoryCounts = activityLogRepository.countActionsByCategory(shop, since);
        Map<String, Long> categoryMap = new HashMap<>();
        for (Object[] row : categoryCounts) {
            categoryMap.put((String) row[0], (Long) row[1]);
        }
        stats.put("actionsByCategory", categoryMap);

        // Count failed actions
        Long failedCount = activityLogRepository.countFailedActions(shop, since);
        stats.put("failedActions", failedCount);

        // Average duration by category
        List<Object[]> avgDurations = activityLogRepository.getAverageDurationByCategory(shop, since);
        Map<String, Double> durationMap = new HashMap<>();
        for (Object[] row : avgDurations) {
            durationMap.put((String) row[0], (Double) row[1]);
        }
        stats.put("avgDurationByCategory", durationMap);

        // Most active users
        List<Object[]> activeUsers = activityLogRepository.findMostActiveUsers(
                shop, since, Pageable.ofSize(10)
        );
        Map<String, Long> userMap = new HashMap<>();
        for (Object[] row : activeUsers) {
            userMap.put((String) row[0], (Long) row[1]);
        }
        stats.put("mostActiveUsers", userMap);

        return stats;
    }

    /**
     * Search activity logs
     */
    @Transactional(readOnly = true)
    public List<AdminActivityLog> searchActivityLogs(ShopifyShop shop, String searchTerm) {
        return activityLogRepository.searchByChangeSummary(shop, searchTerm);
    }

    /**
     * Anonymize old logs for GDPR compliance
     */
    @Transactional
    public int anonymizeOldLogs(int retentionYears) {
        ZonedDateTime cutoffDate = ZonedDateTime.now().minusYears(retentionYears);
        List<AdminActivityLog> logsToAnonymize = activityLogRepository.findLogsToAnonymize(cutoffDate);

        int count = 0;
        for (AdminActivityLog log : logsToAnonymize) {
            log.anonymize();
            activityLogRepository.save(log);
            count++;
        }

        logger.info("Anonymized {} activity logs older than {} years", count, retentionYears);
        return count;
    }

    /**
     * Extract IP address from request, handling proxies
     */
    private String extractIpAddress(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "HTTP_VIA",
                "REMOTE_ADDR"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Handle multiple IPs (take first)
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        return request.getRemoteAddr();
    }
}
