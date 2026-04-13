package com.shopify.api.handler.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Chat tool handler for finding products on sale and current promotions.
 * Finds products with compareAtPrice (discounted) or tagged as "sale"/"special".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GetPromotionsChatToolHandler implements ChatToolHandler {

    private final ProductService productService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getToolName() {
        return "get_promotions";
    }

    @Override
    public String getToolDescription() {
        return "Find products currently on sale or promotion. Returns discounted products with " +
                "original and sale prices. Use when customers ask about deals, sales, specials, " +
                "discounts, or bargains. Can filter by category.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode categoryProp = objectMapper.createObjectNode();
        categoryProp.put("type", "string");
        categoryProp.put("description", "Optional category to filter promotions (e.g., 'PAINT', 'PLASTIC KITS')");
        properties.set("category", categoryProp);

        ObjectNode limitProp = objectMapper.createObjectNode();
        limitProp.put("type", "integer");
        limitProp.put("description", "Number of results to return (default 10)");
        properties.set("limit", limitProp);

        schema.set("properties", properties);
        schema.set("required", objectMapper.createArrayNode());

        return schema;
    }

    @Override
    public Mono<String> execute(JsonNode input) {
        String category = input.has("category") ? input.get("category").asText() : null;
        int limit = input.has("limit") ? Math.min(input.get("limit").asInt(), 20) : 10;

        // Search for products tagged as "sale" or "special"
        StringBuilder query = new StringBuilder("tag:sale OR tag:special OR tag:clearance");
        if (category != null && !category.isEmpty()) {
            query.append(" AND product_type:").append(category);
        }

        log.info("Searching promotions - query: '{}', limit: {}", query, limit);

        return productService.searchProductsReactive(query.toString(), limit)
                .map(results -> {
                    try {
                        return objectMapper.writeValueAsString(results);
                    } catch (Exception e) {
                        return "{\"error\": \"Error formatting promotions\"}";
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error fetching promotions: {}", e.getMessage(), e);
                    return Mono.just("{\"error\": \"Unable to fetch current promotions\"}");
                });
    }
}
