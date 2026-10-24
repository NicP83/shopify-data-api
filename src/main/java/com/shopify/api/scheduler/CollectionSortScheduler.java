package com.shopify.api.scheduler;

import com.shopify.api.service.collection.CollectionSortService;
import com.shopify.api.service.collection.CollectionSortService.CollectionSortResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that reorders Shopify collections so sold-out products sink to
 * the bottom globally (across all pages). Disabled by default — enable with
 * {@code collection-sort.enabled=true}. Ships in dry-run mode
 * ({@code collection-sort.dry-run=true}) so the first runs only log intended
 * moves; flip {@code COLLECTION_SORT_DRY_RUN=false} once validated.
 */
@Component
@ConditionalOnProperty(
        value = "collection-sort.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class CollectionSortScheduler {

    private static final Logger logger = LoggerFactory.getLogger(CollectionSortScheduler.class);

    private final CollectionSortService collectionSortService;

    @Value("${collection-sort.dry-run:true}")
    private boolean dryRun;

    public CollectionSortScheduler(CollectionSortService collectionSortService) {
        this.collectionSortService = collectionSortService;
        logger.info("CollectionSortScheduler initialized");
    }

    /**
     * Reorder all eligible collections. Default cron: every 4 hours.
     */
    @Scheduled(cron = "${collection-sort.schedule.reorder-cron:0 0 */4 * * *}")
    public void reorderCollections() {
        logger.info("=== Scheduled: Collection sort (dryRun={}) ===", dryRun);
        try {
            long startTime = System.currentTimeMillis();
            CollectionSortResult result = collectionSortService.run(dryRun, null);
            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Collection sort completed in {}ms: {}", elapsed, result.summaryLine());
        } catch (Exception e) {
            logger.error("Error running collection sort: {}", e.getMessage(), e);
        }
    }
}
