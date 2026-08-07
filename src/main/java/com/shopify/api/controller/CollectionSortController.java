package com.shopify.api.controller;

import com.shopify.api.filter.ApiKeyAuthFilter;
import com.shopify.api.model.ApiKey;
import com.shopify.api.service.collection.CollectionSortService;
import com.shopify.api.service.collection.CollectionSortService.CollectionSortResult;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * Manual trigger for the collection sort job — lets us dry-run and validate the
 * reorder logic without waiting for the 4-hourly schedule.
 *
 * <p>Lives under the authenticated {@code /api/v1/**} surface (guarded by
 * {@link ApiKeyAuthFilter}) and additionally requires the {@code admin} scope, so
 * an outside party cannot trigger reorders.
 *
 * <p>Examples (with a valid {@code X-API-Key: <admin key>} header):
 * <pre>
 *   POST /api/v1/admin/collection-sort/run?dryRun=true&amp;collectionId=gid://...  (one, sync)
 *   POST /api/v1/admin/collection-sort/run?dryRun=true                           (all, background)
 * </pre>
 * A full run (no {@code collectionId}) executes in the background and returns 202
 * immediately — reordering every collection can exceed the MVC request timeout, so
 * results are written to the logs. A single-collection run is synchronous and
 * returns its result. When {@code dryRun} is omitted it defaults to the configured
 * {@code collection-sort.dry-run} value (true by default).
 */
@RestController
@RequestMapping("/api/v1/admin/collection-sort")
public class CollectionSortController {

    private static final Logger logger = LoggerFactory.getLogger(CollectionSortController.class);

    private final CollectionSortService collectionSortService;
    private final ExecutorService background =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "collection-sort-manual");
                t.setDaemon(true);
                return t;
            });

    @Value("${collection-sort.dry-run:true}")
    private boolean defaultDryRun;

    public CollectionSortController(CollectionSortService collectionSortService) {
        this.collectionSortService = collectionSortService;
    }

    @PostMapping("/run")
    public ResponseEntity<?> run(
            HttpServletRequest request,
            @RequestParam(required = false) Boolean dryRun,
            @RequestParam(required = false) String collectionId) {

        Object attr = request.getAttribute(ApiKeyAuthFilter.ATTR_API_KEY);
        if (!(attr instanceof ApiKey key) || !key.hasScope("admin")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "error", "scope 'admin' required"));
        }

        boolean effectiveDryRun = dryRun != null ? dryRun : defaultDryRun;
        logger.info("POST /api/v1/admin/collection-sort/run dryRun={} collectionId={}", effectiveDryRun, collectionId);

        if (collectionId != null && !collectionId.isBlank()) {
            // Single collection: fast enough to run synchronously and return the result.
            CollectionSortResult result = collectionSortService.run(effectiveDryRun, collectionId);
            return ResponseEntity.ok(result);
        }

        // Full run: potentially long — execute in the background and acknowledge.
        background.submit(() -> {
            try {
                CollectionSortResult result = collectionSortService.run(effectiveDryRun, null);
                logger.info("Manual collection sort (all) finished: {}", result.summaryLine());
            } catch (Exception e) {
                logger.error("Manual collection sort (all) failed: {}", e.getMessage(), e);
            }
        });
        return ResponseEntity.accepted().body(Map.of(
                "success", true,
                "message", "Collection sort started for all collections (dryRun=" + effectiveDryRun
                        + "); see server logs for results."));
    }
}
