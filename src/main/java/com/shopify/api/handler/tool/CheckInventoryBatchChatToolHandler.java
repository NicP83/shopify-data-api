package com.shopify.api.handler.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.model.ChatbotConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Batch variant of {@link CheckInventoryChatToolHandler}: checks stock/availability for MANY products
 * in ONE call, running each check concurrently instead of one-per-model-turn. It delegates each query
 * to the single {@code check_inventory} handler, so the per-product result (variants, stock status,
 * urgency, variant IDs) is byte-for-byte identical — only the number of Claude round-trips changes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckInventoryBatchChatToolHandler implements ChatToolHandler {

    private final CheckInventoryChatToolHandler singleHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_BATCH = 10;
    private static final int MAX_CONCURRENCY = 5;

    @Override
    public String getToolName() {
        return "check_inventory_batch";
    }

    @Override
    public String getToolDescription() {
        return "Check stock availability for MULTIPLE products in a single call. " +
                "PREFER this over calling check_inventory repeatedly whenever you need stock/prices for " +
                "several products at once (a set, kit, or list — e.g. a paint set with multiple colours). " +
                "Each product is checked independently and precisely (same as check_inventory), and results " +
                "are returned grouped per query. This is much faster than many separate checks.";
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
                "List of product names/search terms to check stock for, one per item. " +
                "Provide ALL the products you need in this one call.");
        queriesProp.put("minItems", 1);
        queriesProp.put("maxItems", MAX_BATCH);
        properties.set("queries", queriesProp);

        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("queries");
        schema.set("required", required);

        return schema;
    }

    @Override
    public Mono<String> execute(JsonNode input) {
        JsonNode queriesNode = input.get("queries");
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

        log.info("Batch inventory check - {} queries: {}", queries.size(), queries);

        return Flux.fromIterable(queries)
                .flatMap(query -> {
                    ObjectNode singleInput = objectMapper.createObjectNode();
                    singleInput.put("query", query);
                    // Reuse the single handler verbatim so per-product output is identical.
                    return singleHandler.execute(singleInput)
                            .map(json -> {
                                ObjectNode entry = objectMapper.createObjectNode();
                                entry.put("query", query);
                                try {
                                    entry.set("result", objectMapper.readTree(json));
                                } catch (Exception e) {
                                    entry.put("result", json);
                                }
                                return entry;
                            })
                            .onErrorResume(e -> {
                                ObjectNode entry = objectMapper.createObjectNode();
                                entry.put("query", query);
                                entry.put("error", e.getMessage());
                                return Mono.just(entry);
                            });
                }, MAX_CONCURRENCY)
                .collectList()
                .map(entries -> {
                    ObjectNode root = objectMapper.createObjectNode();
                    ArrayNode results = objectMapper.createArrayNode();
                    entries.forEach(results::add);
                    root.set("results", results);
                    return root.toString();
                })
                .onErrorResume(e -> {
                    log.error("Error executing batch inventory check: {}", e.getMessage(), e);
                    return Mono.just("{\"error\": \"Error executing batch inventory check: " + e.getMessage() + "\"}");
                });
    }

    @Override
    public boolean isEnabled(ChatbotConfig config) {
        return singleHandler.isEnabled(config);
    }

    @Override
    public boolean validateInput(JsonNode input) {
        JsonNode queries = input.get("queries");
        return queries != null && queries.isArray() && queries.size() > 0;
    }
}
