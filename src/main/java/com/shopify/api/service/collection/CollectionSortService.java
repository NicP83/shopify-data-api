package com.shopify.api.service.collection;

import com.shopify.api.client.ShopifyGraphQLClient;
import com.shopify.api.model.GraphQLRequest;
import com.shopify.api.model.GraphQLResponse;
import com.shopify.api.util.PreorderTagUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Reorders products within Shopify collections so that genuinely sold-out
 * products sink to the bottom while sellable / pre-order / back-order /
 * "coming soon" products stay on top — the global (across-all-pages) counterpart
 * to the per-page Liquid regroup in the live theme's main-collection section.
 *
 * <p>Availability is judged with the SAME rule as the theme (a product is kept on
 * top if any variant is sellable — in stock, oversell/continue, or untracked — or
 * it is pre-order / coming-soon), NOT the chatbot's simpler {@code qty > 0} rule,
 * so the backend order and the storefront order agree.
 *
 * <p>Only automated/manual collections whose {@code sortOrder == MANUAL} can be
 * reordered via {@code collectionReorderProducts}; others are skipped. Collections
 * larger than {@code max-products} are skipped in v1. Runs in dry-run mode unless
 * told otherwise. All Shopify calls go through {@link ShopifyGraphQLClient}, which
 * already applies rate limiting + backoff. The runtime admin token
 * ({@code SHOPIFY_ACCESS_TOKEN}) must carry the {@code write_products} scope.
 *
 * <p>Only one run may execute at a time (scheduler + manual trigger share a lock)
 * to avoid interleaved reorders on the same collection.
 */
@Service
public class CollectionSortService {

    private static final Logger logger = LoggerFactory.getLogger(CollectionSortService.class);
    private static final int PAGE_SIZE = 250;
    private static final int VARIANT_PAGE_SIZE = 250; // Shopify max page size
    private static final int JOB_POLL_ATTEMPTS = 30;

    /** Strict guard against injection / malformed ids from the manual-trigger endpoint. */
    private static final Pattern COLLECTION_GID = Pattern.compile("^gid://shopify/Collection/[0-9]+$");

    private final ShopifyGraphQLClient graphQLClient;
    /** Serialises runs so the scheduler and manual endpoint never reorder concurrently. */
    private final ReentrantLock runLock = new ReentrantLock();

    @Value("${collection-sort.max-products:2000}")
    private int maxProducts;

    @Value("${collection-sort.batch-size:250}")
    private int batchSize;

    @Value("${collection-sort.throttle-ms:1000}")
    private long throttleMs;

    /** Reuse the chatbot's configured pre-order tags so definitions stay in sync. */
    @Value("${chatbot.tools.preorder-tags:preorder,pre-order}")
    private String preorderTagsCsv;

    /** Parsed once — the CSV never changes at runtime. */
    private Set<String> preorderTags = Set.of();

    public CollectionSortService(ShopifyGraphQLClient graphQLClient) {
        this.graphQLClient = graphQLClient;
    }

    @PostConstruct
    void init() {
        this.preorderTags = PreorderTagUtil.parsePreorderTags(preorderTagsCsv);
    }

    // ---------- public API ----------

    /**
     * Reorder collections. When {@code onlyCollectionId} is non-blank only that
     * collection is processed (useful for testing); otherwise all collections are.
     * If another run is already in progress this returns a {@code busy} result
     * without doing any work.
     */
    public CollectionSortResult run(boolean dryRun, String onlyCollectionId) {
        // Validate up front so a bad id fails fast (and before taking the lock).
        String only = (onlyCollectionId != null && !onlyCollectionId.isBlank())
                ? requireValidCollectionId(onlyCollectionId) : null;

        if (!runLock.tryLock()) {
            logger.warn("Collection sort already running; skipping this invocation");
            return CollectionSortResult.busy();
        }
        try {
            return doRun(dryRun, only);
        } finally {
            runLock.unlock();
        }
    }

    private CollectionSortResult doRun(boolean dryRun, String onlyCollectionId) {
        long start = System.currentTimeMillis();
        logger.info("=== Collection sort run (dryRun={}, only={}) ===", dryRun, onlyCollectionId);

        List<CollectionRef> collections = onlyCollectionId != null
                ? List.of(fetchCollectionRef(onlyCollectionId))
                : listCollections();

        CollectionSortResult result = new CollectionSortResult();
        for (CollectionRef col : collections) {
            CollectionSummary summary;
            try {
                summary = processCollection(col, dryRun);
            } catch (Exception e) {
                logger.error("Error sorting collection {} ({}): {}", col.title(), col.id(), e.getMessage(), e);
                summary = new CollectionSummary(col.id(), col.title(), col.productsCount(), 0, 0, "ERROR");
            }
            result.add(summary);
            // Only throttle after collections that actually hit the Shopify API.
            if (summary.madeApiCalls()) {
                sleep(throttleMs);
            }
        }

        logger.info("=== Collection sort done in {}ms: {} ===",
                System.currentTimeMillis() - start, result.summaryLine());
        return result;
    }

    // ---------- per-collection ----------

    private CollectionSummary processCollection(CollectionRef col, boolean dryRun) {
        if (!"MANUAL".equalsIgnoreCase(col.sortOrder())) {
            logger.info("Skip '{}' ({}): sortOrder={} (needs MANUAL to reorder)", col.title(), col.id(), col.sortOrder());
            return new CollectionSummary(col.id(), col.title(), col.productsCount(), 0, 0, "SKIPPED_SORT_ORDER");
        }
        if (col.productsCount() > maxProducts) {
            logger.info("Skip '{}' ({}): {} products > cap {}", col.title(), col.id(), col.productsCount(), maxProducts);
            return new CollectionSummary(col.id(), col.title(), col.productsCount(), 0, 0, "SKIPPED_SIZE");
        }

        List<ProductInfo> products = fetchProducts(col.id());
        if (products == null) {
            logger.info("Skip '{}' ({}): collection not found (deleted?)", col.title(), col.id());
            return new CollectionSummary(col.id(), col.title(), col.productsCount(), 0, 0, "SKIPPED_MISSING");
        }

        List<Map<String, Object>> moves = computeMoves(products);
        long soldOut = products.stream().filter(p -> !p.keepOnTop()).count();

        if (moves.isEmpty()) {
            logger.info("'{}' ({}): {} products, {} sold-out — already grouped, no moves",
                    col.title(), col.id(), products.size(), soldOut);
            return new CollectionSummary(col.id(), col.title(), products.size(), (int) soldOut, 0, "ALREADY_SORTED");
        }

        if (dryRun) {
            logger.info("[DRY-RUN] '{}' ({}): would move {} sold-out product(s) to the bottom of {} total",
                    col.title(), col.id(), moves.size(), products.size());
            return new CollectionSummary(col.id(), col.title(), products.size(), (int) soldOut, moves.size(), "DRY_RUN");
        }

        int applied = 0;
        for (int i = 0; i < moves.size(); i += batchSize) {
            List<Map<String, Object>> batch = moves.subList(i, Math.min(i + batchSize, moves.size()));
            String jobId = reorder(col.id(), batch);
            pollJob(jobId); // throws if the job never settles, aborting before the next batch
            applied += batch.size();
            if (i + batchSize < moves.size()) {
                sleep(throttleMs);
            }
        }
        logger.info("Reordered '{}' ({}): applied {} move(s), {} sold-out sunk of {} total",
                col.title(), col.id(), applied, soldOut, products.size());
        return new CollectionSummary(col.id(), col.title(), products.size(), (int) soldOut, applied, "REORDERED");
    }

    // ---------- pure logic (unit-tested) ----------

    /**
     * Build the reorder moves that send every sold-out product to the bottom while
     * preserving the relative order of everything else. Returns an empty list when
     * the collection is already grouped (no sold-out product precedes a kept one).
     *
     * <p>Shopify's {@code collectionReorderProducts} applies moves SEQUENTIALLY, each
     * {@code newPosition} interpreted against the array state after the previous move
     * (verified empirically) — NOT as an absolute final index. So we move each sold-out
     * product, in original order, to the last index. Moving to the last index stacks
     * them at the end in order and shifts everything else up, yielding
     * {@code keepTop... , sink...}.
     */
    static List<Map<String, Object>> computeMoves(List<ProductInfo> products) {
        if (products.isEmpty() || isAlreadyGrouped(products)) {
            return List.of();
        }
        String lastIndex = String.valueOf(products.size() - 1);
        List<Map<String, Object>> moves = new ArrayList<>();
        for (ProductInfo p : products) {
            if (!p.keepOnTop()) {
                moves.add(Map.of("id", p.id(), "newPosition", lastIndex));
            }
        }
        return moves;
    }

    /** Grouped == once the first sold-out product appears, everything after is sold-out. */
    static boolean isAlreadyGrouped(List<ProductInfo> products) {
        boolean seenSoldOut = false;
        for (ProductInfo p : products) {
            if (p.keepOnTop()) {
                if (seenSoldOut) return false;
            } else {
                seenSoldOut = true;
            }
        }
        return true;
    }

    /**
     * Availability rule mirroring the storefront theme. {@code variantsTruncated} is
     * true when the product has more variants than we fetched — in that case we cannot
     * be sure none of the unseen variants is sellable, so we conservatively keep the
     * product on top rather than risk sinking a purchasable product.
     */
    boolean isKeepOnTop(List<String> tags, String templateSuffix, List<VariantInfo> variants, boolean variantsTruncated) {
        if (PreorderTagUtil.isPreorder(tags, preorderTags)) return true;
        if ("pre-order".equalsIgnoreCase(templateSuffix)) return true;
        if (tags != null) {
            for (String t : tags) {
                if (t != null && t.trim().equalsIgnoreCase("coming soon")) return true;
            }
        }
        if (variants != null) {
            for (VariantInfo v : variants) {
                boolean sellable = "CONTINUE".equalsIgnoreCase(v.inventoryPolicy())
                        || Boolean.FALSE.equals(v.tracked())
                        || v.inventoryQuantity() > 0;
                if (sellable) return true;
            }
        }
        return variantsTruncated;
    }

    // ---------- Shopify calls ----------

    @SuppressWarnings("unchecked")
    private List<CollectionRef> listCollections() {
        List<CollectionRef> all = new ArrayList<>();
        String cursor = null;
        boolean hasNext = true;
        int page = 0;
        while (hasNext && page < 40) { // 40 * 250 = 10k collections safety cap
            page++;
            String query = """
                query($after: String) {
                  collections(first: 250, after: $after) {
                    edges { node { id title sortOrder productsCount { count } } }
                    pageInfo { hasNextPage endCursor }
                  }
                }
                """;
            GraphQLResponse resp = graphQLClient.executeQuery(
                    new GraphQLRequest(query, mapOf("after", cursor)));
            requireNoErrors(resp, "listCollections page " + page);
            Map<String, Object> conns = (Map<String, Object>) resp.getData().get("collections");
            for (Map<String, Object> edge : (List<Map<String, Object>>) conns.get("edges")) {
                all.add(toCollectionRef((Map<String, Object>) edge.get("node")));
            }
            Map<String, Object> pageInfo = (Map<String, Object>) conns.get("pageInfo");
            hasNext = Boolean.TRUE.equals(pageInfo.get("hasNextPage"));
            cursor = (String) pageInfo.get("endCursor");
        }
        logger.info("Discovered {} collections across {} page(s)", all.size(), page);
        return all;
    }

    @SuppressWarnings("unchecked")
    private CollectionRef fetchCollectionRef(String collectionId) {
        String query = """
            query($id: ID!) { collection(id: $id) { id title sortOrder productsCount { count } } }
            """;
        GraphQLResponse resp = graphQLClient.executeQuery(
                new GraphQLRequest(query, Map.of("id", collectionId)));
        requireNoErrors(resp, "fetchCollectionRef");
        Map<String, Object> node = (Map<String, Object>) resp.getData().get("collection");
        if (node == null) {
            throw new RuntimeException("Collection not found: " + collectionId);
        }
        return toCollectionRef(node);
    }

    @SuppressWarnings("unchecked")
    private CollectionRef toCollectionRef(Map<String, Object> node) {
        Map<String, Object> pc = (Map<String, Object>) node.get("productsCount");
        int count = pc != null && pc.get("count") != null ? ((Number) pc.get("count")).intValue() : 0;
        return new CollectionRef((String) node.get("id"), (String) node.get("title"),
                (String) node.get("sortOrder"), count);
    }

    /**
     * Fetch all products of a collection in current order. Returns {@code null} if the
     * collection no longer exists. Throws if the collection is larger than the fetch cap
     * (derived from {@code max-products}) so we never reorder a partial view.
     */
    @SuppressWarnings("unchecked")
    List<ProductInfo> fetchProducts(String collectionId) {
        List<ProductInfo> products = new ArrayList<>();
        String cursor = null;
        boolean hasNext = true;
        int page = 0;
        int maxPages = Math.max(2, maxProducts / PAGE_SIZE + 2);
        while (hasNext && page < maxPages) {
            page++;
            String query = """
                query($id: ID!, $after: String) {
                  collection(id: $id) {
                    products(first: 250, after: $after) {
                      edges {
                        node {
                          id
                          templateSuffix
                          tags
                          variants(first: 250) {
                            edges { node { inventoryQuantity inventoryPolicy inventoryItem { tracked } } }
                            pageInfo { hasNextPage }
                          }
                        }
                      }
                      pageInfo { hasNextPage endCursor }
                    }
                  }
                }
                """;
            Map<String, Object> vars = new HashMap<>();
            vars.put("id", collectionId);
            vars.put("after", cursor);
            GraphQLResponse resp = graphQLClient.executeQuery(new GraphQLRequest(query, vars));
            requireNoErrors(resp, "fetchProducts page " + page);
            Map<String, Object> collection = (Map<String, Object>) resp.getData().get("collection");
            if (collection == null) {
                if (page == 1) {
                    return null; // deleted between listing and fetch
                }
                break; // disappeared mid-pagination; use what we have
            }
            Map<String, Object> conn = (Map<String, Object>) collection.get("products");
            for (Map<String, Object> edge : (List<Map<String, Object>>) conn.get("edges")) {
                Map<String, Object> node = (Map<String, Object>) edge.get("node");
                products.add(parseProduct(node));
            }
            Map<String, Object> pageInfo = (Map<String, Object>) conn.get("pageInfo");
            hasNext = Boolean.TRUE.equals(pageInfo.get("hasNextPage"));
            cursor = (String) pageInfo.get("endCursor");
        }
        if (hasNext) {
            throw new RuntimeException("Collection " + collectionId + " exceeds fetch cap (" + products.size()
                    + "+ products); skipping to avoid reordering a partial view");
        }
        return products;
    }

    @SuppressWarnings("unchecked")
    private ProductInfo parseProduct(Map<String, Object> node) {
        String id = (String) node.get("id");
        String templateSuffix = (String) node.get("templateSuffix");
        List<String> tags = (List<String>) node.get("tags");
        List<VariantInfo> variants = new ArrayList<>();
        boolean variantsTruncated = false;
        Map<String, Object> variantsConn = (Map<String, Object>) node.get("variants");
        if (variantsConn != null) {
            for (Map<String, Object> vEdge : (List<Map<String, Object>>) variantsConn.get("edges")) {
                Map<String, Object> v = (Map<String, Object>) vEdge.get("node");
                int qty = v.get("inventoryQuantity") != null ? ((Number) v.get("inventoryQuantity")).intValue() : 0;
                String policy = (String) v.get("inventoryPolicy");
                Boolean tracked = null;
                Map<String, Object> invItem = (Map<String, Object>) v.get("inventoryItem");
                if (invItem != null && invItem.get("tracked") != null) {
                    tracked = (Boolean) invItem.get("tracked");
                }
                variants.add(new VariantInfo(qty, policy, tracked));
            }
            Map<String, Object> vPageInfo = (Map<String, Object>) variantsConn.get("pageInfo");
            variantsTruncated = vPageInfo != null && Boolean.TRUE.equals(vPageInfo.get("hasNextPage"));
        }
        if (variantsTruncated) {
            logger.warn("Product {} has more than {} variants; treating as sellable (kept on top)", id, VARIANT_PAGE_SIZE);
        }
        return new ProductInfo(id, isKeepOnTop(tags, templateSuffix, variants, variantsTruncated));
    }

    @SuppressWarnings("unchecked")
    private String reorder(String collectionId, List<Map<String, Object>> moves) {
        String mutation = """
            mutation reorder($id: ID!, $moves: [MoveInput!]!) {
              collectionReorderProducts(id: $id, moves: $moves) {
                job { id done }
                userErrors { field message }
              }
            }
            """;
        GraphQLRequest request = new GraphQLRequest(mutation, Map.of("id", collectionId, "moves", moves));
        GraphQLResponse resp = graphQLClient.executeQuery(request);
        requireNoErrors(resp, "collectionReorderProducts");

        Map<String, Object> payload = (Map<String, Object>) resp.getData().get("collectionReorderProducts");
        if (payload == null) {
            throw new RuntimeException("collectionReorderProducts returned no payload");
        }
        List<Map<String, Object>> userErrors = (List<Map<String, Object>>) payload.get("userErrors");
        if (userErrors != null && !userErrors.isEmpty()) {
            throw new RuntimeException("collectionReorderProducts userErrors: " + userErrors);
        }
        Map<String, Object> job = (Map<String, Object>) payload.get("job");
        return job != null ? (String) job.get("id") : null;
    }

    @SuppressWarnings("unchecked")
    private void pollJob(String jobId) {
        if (jobId == null) return;
        for (int i = 0; i < JOB_POLL_ATTEMPTS; i++) {
            String query = "query($id: ID!) { job(id: $id) { id done } }";
            GraphQLResponse resp = graphQLClient.executeQuery(new GraphQLRequest(query, Map.of("id", jobId)));
            requireNoErrors(resp, "job poll");
            Map<String, Object> job = (Map<String, Object>) resp.getData().get("job");
            if (job == null || Boolean.TRUE.equals(job.get("done"))) {
                return;
            }
            sleep(1000);
        }
        // Do NOT proceed to the next batch against an unsettled reorder.
        throw new RuntimeException("Reorder job " + jobId + " did not complete within the poll window");
    }

    // ---------- helpers ----------

    private void requireNoErrors(GraphQLResponse resp, String where) {
        if (resp == null) {
            throw new RuntimeException(where + ": null response");
        }
        if (resp.hasErrors()) {
            throw new RuntimeException(where + " errors: " + resp.getErrors());
        }
        if (resp.getData() == null) {
            throw new RuntimeException(where + ": no data");
        }
    }

    private String requireValidCollectionId(String id) {
        if (id == null || !COLLECTION_GID.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Invalid collectionId (expected gid://shopify/Collection/<number>): " + id);
        }
        return id;
    }

    /** Single-entry map that tolerates a null value (Map.of does not). */
    private static Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> m = new HashMap<>();
        m.put(key, value);
        return m;
    }

    private void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------- value types ----------

    record CollectionRef(String id, String title, String sortOrder, int productsCount) {}

    record ProductInfo(String id, boolean keepOnTop) {}

    record VariantInfo(int inventoryQuantity, String inventoryPolicy, Boolean tracked) {}

    public record CollectionSummary(String id, String title, int total, int soldOut, int moves, String status) {
        /** True for statuses that issued at least one Shopify call (so throttling applies). */
        boolean madeApiCalls() {
            return !status.startsWith("SKIPPED");
        }
    }

    /** Aggregate result returned to the scheduler / controller. */
    public static class CollectionSortResult {
        private final List<CollectionSummary> details = new ArrayList<>();
        private int reordered, alreadySorted, dryRun, skipped, errors;
        private boolean busy;

        static CollectionSortResult busy() {
            CollectionSortResult r = new CollectionSortResult();
            r.busy = true;
            return r;
        }

        void add(CollectionSummary s) {
            details.add(s);
            switch (s.status()) {
                case "REORDERED" -> reordered++;
                case "ALREADY_SORTED" -> alreadySorted++;
                case "DRY_RUN" -> dryRun++;
                case "SKIPPED_SIZE", "SKIPPED_SORT_ORDER", "SKIPPED_MISSING" -> skipped++;
                case "ERROR" -> errors++;
                default -> { }
            }
        }

        public List<CollectionSummary> getDetails() { return details; }
        public int getCollectionsProcessed() { return details.size(); }
        public int getReordered() { return reordered; }
        public int getAlreadySorted() { return alreadySorted; }
        public int getDryRun() { return dryRun; }
        public int getSkipped() { return skipped; }
        public int getErrors() { return errors; }
        public boolean isBusy() { return busy; }

        public String summaryLine() {
            if (busy) return "busy=true (another run in progress)";
            return String.format("processed=%d reordered=%d dryRun=%d alreadySorted=%d skipped=%d errors=%d",
                    getCollectionsProcessed(), reordered, dryRun, alreadySorted, skipped, errors);
        }
    }
}
