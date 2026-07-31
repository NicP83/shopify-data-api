package com.shopify.api.handler.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.model.ChatbotConfig;
import com.shopify.api.service.ChatbotConfigService;
import com.shopify.api.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Batch variant of {@link SearchProductsChatToolHandler}: looks up MANY products in ONE tool call,
 * running each search concurrently instead of one-per-model-turn. Each query is searched with the
 * SAME engine, args, cache, and fallback as {@code search_products} — so precision, coverage, and
 * result shape are identical; only the number of Claude round-trips changes (N -> 1).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchProductsBatchChatToolHandler implements ChatToolHandler {

    private final ProductService productService;
    private final ChatbotConfigService chatbotConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Cap the batch so a pathological request can't burst past the Shopify rate budget. */
    private static final int MAX_BATCH = 10;
    /** Keep in-flight Shopify searches near the rate budget. */
    private static final int MAX_CONCURRENCY = 5;

    @Override
    public String getToolName() {
        return "search_products_batch";
    }

    @Override
    public String getToolDescription() {
        return "Search the product catalog for MULTIPLE products in a single call. " +
                "PREFER this over calling search_products repeatedly whenever the customer asks about " +
                "several products, a set, a kit, or a list (e.g. a paint set with several colours). " +
                "Each query is searched independently and precisely with the same engine as search_products; " +
                "results are returned grouped per query. This is faster than many separate searches.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode queriesProp = objectMapper.createObjectNode();
        queriesProp.put("type", "array");
        ObjectNode items = objectMapper.createObjectNode();
        items.put("type", "string");
        queriesProp.set("items", items);
        queriesProp.put("description",
                "List of independent product search queries, one per item (e.g. one per paint colour). " +
                "Each is searched precisely and separately. Provide all products you need in one call.");
        queriesProp.put("minItems", 1);
        queriesProp.put("maxItems", MAX_BATCH);
        properties.set("queries", queriesProp);

        ObjectNode productTypeProp = objectMapper.createObjectNode();
        productTypeProp.put("type", "string");
        productTypeProp.put("description", "Optional shared product type filter applied to every query.");
        properties.set("productType", productTypeProp);

        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("queries");
        schema.set("required", required);

        return schema;
    }

    @Override
    public Mono<String> execute(JsonNode input) {
        JsonNode queriesNode = input.get("queries");
        // De-duplicate (preserving order) and cap the batch size.
        Set<String> queries = new LinkedHashSet<>();
        if (queriesNode != null && queriesNode.isArray()) {
            for (JsonNode q : queriesNode) {
                String s = q.asText().trim();
                if (!s.isEmpty()) {
                    queries.add(s);
                }
                if (queries.size() >= MAX_BATCH) {
                    break;
                }
            }
        }
        if (queries.isEmpty()) {
            return Mono.just("{\"error\": \"No valid queries provided\"}");
        }

        int maxResults = chatbotConfigService.getConfig().getMaxSearchResults();
        String productType = input.hasNonNull("productType") ? input.get("productType").asText() : null;

        log.info("Batch product search - {} queries: {}, Max Results: {}", queries.size(), queries, maxResults);

        return Flux.fromIterable(queries)
                .flatMap(query -> productService.searchProductsReactive(query, maxResults, false, productType)
                                .map(result -> {
                                    ObjectNode entry = objectMapper.createObjectNode();
                                    entry.put("query", query);
                                    entry.set("result", objectMapper.valueToTree(result));
                                    return entry;
                                })
                                .onErrorResume(e -> {
                                    log.error("Batch search failed for '{}': {}", query, e.getMessage());
                                    ObjectNode entry = objectMapper.createObjectNode();
                                    entry.put("query", query);
                                    entry.put("error", e.getMessage());
                                    return Mono.just(entry);
                                }),
                        MAX_CONCURRENCY)
                .collectList()
                .map(entries -> {
                    ObjectNode root = objectMapper.createObjectNode();
                    ArrayNode results = objectMapper.createArrayNode();
                    entries.forEach(results::add);
                    root.set("results", results);
                    return root.toString();
                })
                .onErrorResume(e -> {
                    log.error("Error executing batch product search: {}", e.getMessage(), e);
                    return Mono.just("{\"error\": \"Error executing batch search: " + e.getMessage() + "\"}");
                });
    }

    @Override
    public boolean isEnabled(ChatbotConfig config) {
        return config.isEnableProductSearch();
    }

    @Override
    public boolean validateInput(JsonNode input) {
        JsonNode queries = input.get("queries");
        return queries != null && queries.isArray() && queries.size() > 0;
    }
}
