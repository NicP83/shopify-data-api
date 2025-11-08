package com.shopify.api.scheduler;

import com.shopify.api.repository.inventory.SalesVelocityRepository;
import com.shopify.api.repository.inventory.StockAlertRepository;
import com.shopify.api.service.inventory.InventoryAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Scheduled jobs for inventory analysis
 * Runs automated analysis, checks for low stock, and generates recommendations
 */
@Component
@ConditionalOnProperty(
        value = "inventory-analysis.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class InventoryAnalysisScheduler {

    private static final Logger logger = LoggerFactory.getLogger(InventoryAnalysisScheduler.class);

    private final InventoryAnalysisService analysisService;
    private final SalesVelocityRepository velocityRepository;
    private final StockAlertRepository alertRepository;

    public InventoryAnalysisScheduler(
            InventoryAnalysisService analysisService,
            SalesVelocityRepository velocityRepository,
            StockAlertRepository alertRepository) {
        this.analysisService = analysisService;
        this.velocityRepository = velocityRepository;
        this.alertRepository = alertRepository;
        logger.info("InventoryAnalysisScheduler initialized");
    }

    /**
     * Update sales velocity for all products
     * Runs every hour
     * Cron: 0 0 * * * * (At minute 0 of every hour)
     */
    @Scheduled(cron = "${inventory-analysis.schedule.velocity-update-cron:0 0 * * * *}")
    public void updateSalesVelocity() {
        logger.info("=== Scheduled: Update Sales Velocity (Hourly) ===");

        if (!analysisService.isEnabled()) {
            logger.warn("Inventory analysis disabled, skipping velocity update");
            return;
        }

        try {
            long startTime = System.currentTimeMillis();

            // Find velocities that are stale (more than 1 hour old)
            LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
            var staleVelocities = velocityRepository.findByLastCalculatedBefore(cutoff);

            logger.info("Found {} products with stale velocity data", staleVelocities.size());

            // TODO: Implement batch velocity update
            // For now, just log the count
            // In a full implementation, you would:
            // 1. Get list of all SKUs from ERP
            // 2. For each SKU, call analysisService.analyzeSingleProduct(sku)
            // 3. This would be done in batches to avoid overwhelming the system

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Velocity update check completed in {}ms", elapsed);

        } catch (Exception e) {
            logger.error("Error updating sales velocity: {}", e.getMessage(), e);
        }
    }

    /**
     * Check for low stock across all products
     * Runs every 6 hours
     * Cron: 0 0 star-slash-6 * * * (At minute 0 past every 6th hour)
     */
    @Scheduled(cron = "${inventory-analysis.schedule.low-stock-check-cron:0 0 */6 * * *}")
    public void checkLowStock() {
        logger.info("=== Scheduled: Check Low Stock (Every 6 hours) ===");

        if (!analysisService.isEnabled()) {
            logger.warn("Inventory analysis disabled, skipping low stock check");
            return;
        }

        try {
            long startTime = System.currentTimeMillis();

            // Get count of current unresolved alerts
            long unresolvedCount = alertRepository.countByResolvedFalse();

            logger.info("Current unresolved alerts: {}", unresolvedCount);

            // TODO: Implement comprehensive low stock check
            // In a full implementation:
            // 1. Get all products from inventory
            // 2. For each product, check if stock is below reorder point
            // 3. Create alerts for products that are low on stock
            // 4. This would be done in batches

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Low stock check completed in {}ms", elapsed);

        } catch (Exception e) {
            logger.error("Error checking low stock: {}", e.getMessage(), e);
        }
    }

    /**
     * Generate daily order recommendations
     * Runs every day at 8 AM
     * Cron: 0 0 8 * * * (At 08:00)
     */
    @Scheduled(cron = "${inventory-analysis.schedule.recommendations-cron:0 0 8 * * *}")
    public void generateDailyRecommendations() {
        logger.info("=== Scheduled: Generate Daily Recommendations (8 AM) ===");

        if (!analysisService.isEnabled()) {
            logger.warn("Inventory analysis disabled, skipping recommendations");
            return;
        }

        try {
            long startTime = System.currentTimeMillis();

            // Get pending recommendations count
            var pendingRecs = analysisService.generateRecommendations()
                    .collectList()
                    .block();

            logger.info("Current pending recommendations: {}", pendingRecs != null ? pendingRecs.size() : 0);

            // TODO: Implement daily recommendation generation
            // In a full implementation:
            // 1. Analyze all products with low stock
            // 2. Generate recommendations for products below reorder point
            // 3. Send notification/email with summary
            // 4. This would be done in batches

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Daily recommendations check completed in {}ms", elapsed);

        } catch (Exception e) {
            logger.error("Error generating recommendations: {}", e.getMessage(), e);
        }
    }

    /**
     * Clean up old data
     * Runs daily at 2 AM
     * Cron: 0 0 2 * * * (At 02:00)
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldData() {
        logger.info("=== Scheduled: Cleanup Old Data (2 AM) ===");

        try {
            long startTime = System.currentTimeMillis();

            // Auto-resolve alerts older than 30 days
            LocalDateTime alertCutoff = LocalDateTime.now().minusDays(30);
            int resolvedAlerts = alertRepository.autoResolveOldAlerts(alertCutoff);
            logger.info("Auto-resolved {} old alerts", resolvedAlerts);

            // Delete resolved alerts older than 90 days
            LocalDateTime deleteCutoff = LocalDateTime.now().minusDays(90);
            int deletedAlerts = alertRepository.deleteOldResolvedAlerts(deleteCutoff);
            logger.info("Deleted {} old resolved alerts", deletedAlerts);

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Cleanup completed in {}ms", elapsed);

        } catch (Exception e) {
            logger.error("Error during cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Manual trigger for full inventory analysis
     * This can be called via API or admin interface
     */
    public void triggerFullAnalysis() {
        logger.info("=== Manual Trigger: Full Inventory Analysis ===");

        if (!analysisService.isEnabled()) {
            logger.warn("Inventory analysis disabled");
            return;
        }

        try {
            long startTime = System.currentTimeMillis();

            // Get all existing SKUs from velocity table
            var allSkus = velocityRepository.findAll()
                    .stream()
                    .map(v -> v.getSku())
                    .distinct()
                    .toList();

            if (allSkus.isEmpty()) {
                logger.warn("No SKUs found in velocity table to analyze");
                return;
            }

            logger.info("Found {} unique SKUs to analyze", allSkus.size());

            int successCount = 0;
            int errorCount = 0;

            // Process in batches of 5
            for (int i = 0; i < allSkus.size(); i += 5) {
                int end = Math.min(i + 5, allSkus.size());
                var batch = allSkus.subList(i, end);

                logger.info("Processing batch {}-{} of {}", i + 1, end, allSkus.size());

                for (String sku : batch) {
                    try {
                        analysisService.analyzeSingleProduct(sku).block();
                        successCount++;
                        logger.debug("Analyzed SKU: {}", sku);
                    } catch (Exception e) {
                        errorCount++;
                        logger.error("Error analyzing SKU {}: {}", sku, e.getMessage());
                    }
                }

                // Small delay between batches to avoid overwhelming the system
                if (end < allSkus.size()) {
                    try {
                        Thread.sleep(1000); // 1 second delay
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Full analysis completed in {}ms: {} successful, {} errors",
                    elapsed, successCount, errorCount);

        } catch (Exception e) {
            logger.error("Error during full analysis: {}", e.getMessage(), e);
        }
    }

    /**
     * Batch analyze specific SKUs
     * Used for targeted analysis of a subset of products
     */
    public void analyzeBatch(java.util.List<String> skus) {
        logger.info("=== Batch Analysis: {} SKUs ===", skus.size());

        if (!analysisService.isEnabled()) {
            logger.warn("Inventory analysis disabled");
            return;
        }

        try {
            int successCount = 0;
            int errorCount = 0;

            for (String sku : skus) {
                try {
                    analysisService.analyzeSingleProduct(sku).block();
                    successCount++;
                    logger.debug("Analyzed SKU: {}", sku);
                } catch (Exception e) {
                    errorCount++;
                    logger.error("Error analyzing SKU {}: {}", sku, e.getMessage());
                }
            }

            logger.info("Batch analysis completed: {} successful, {} errors", successCount, errorCount);

        } catch (Exception e) {
            logger.error("Error during batch analysis: {}", e.getMessage(), e);
        }
    }
}
