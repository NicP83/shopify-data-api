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
 * Chat tool handler for browsing and filtering products.
 * Supports filtering by category, vendor, price range, and sorting.
 * Useful for "show me new arrivals", "what do you have under $50", "browse paints" etc.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BrowseProductsChatToolHandler implements ChatToolHandler {

    private final ProductService productService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getToolName() {
        return "browse_products";
    }

    @Override
    public String getToolDescription() {
        return "Browse and filter products by category, vendor, price range, or sort order. " +
                "Use when customers want to browse categories (e.g., 'show me paints'), " +
                "filter by price (e.g., 'kits under $50'), see new arrivals, " +
                "or explore by brand/vendor. More targeted than search_products for browsing.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode categoryProp = objectMapper.createObjectNode();
        categoryProp.put("type", "string");
        categoryProp.put("description", "Product category/type to browse (e.g., 'PLASTIC KITS', 'PAINT', 'TOOLS')");
        properties.set("category", categoryProp);

        ObjectNode vendorProp = objectMapper.createObjectNode();
        vendorProp.put("type", "string");
        vendorProp.put("description", "Brand/vendor to filter by (e.g., 'Tamiya', 'Bandai', 'Vallejo')");
        properties.set("vendor", vendorProp);

        ObjectNode minPriceProp = objectMapper.createObjectNode();
        minPriceProp.put("type", "number");
        minPriceProp.put("description", "Minimum price filter");
        properties.set("min_price", minPriceProp);

        ObjectNode maxPriceProp = objectMapper.createObjectNode();
        maxPriceProp.put("type", "number");
        maxPriceProp.put("description", "Maximum price filter");
        properties.set("max_price", maxPriceProp);

        ObjectNode sortProp = objectMapper.createObjectNode();
        sortProp.put("type", "string");
        sortProp.put("description", "Sort order: 'newest', 'price_low', 'price_high', or 'best_selling'");
        properties.set("sort_by", sortProp);

        ObjectNode limitProp = objectMapper.createObjectNode();
        limitProp.put("type", "integer");
        limitProp.put("description", "Number of results to return (default 10, max 20)");
        properties.set("limit", limitProp);

        schema.set("properties", properties);
        // No required fields — all filters are optional
        schema.set("required", objectMapper.createArrayNode());

        return schema;
    }

    @Override
    public Mono<String> execute(JsonNode input) {
        String category = input.has("category") ? input.get("category").asText() : null;
        String vendor = input.has("vendor") ? input.get("vendor").asText() : null;
        Double minPrice = input.has("min_price") ? input.get("min_price").asDouble() : null;
        Double maxPrice = input.has("max_price") ? input.get("max_price").asDouble() : null;
        String sortBy = input.has("sort_by") ? input.get("sort_by").asText() : null;
        int limit = input.has("limit") ? Math.min(input.get("limit").asInt(), 20) : 10;

        // Build Shopify search query from filters
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("status:active");

        if (category != null && !category.isEmpty()) {
            queryBuilder.append(" AND product_type:").append(escapeQuery(category));
        }
        if (vendor != null && !vendor.isEmpty()) {
            queryBuilder.append(" AND vendor:").append(escapeQuery(vendor));
        }
        if (minPrice != null) {
            queryBuilder.append(" AND variants.price:>=").append(minPrice);
        }
        if (maxPrice != null) {
            queryBuilder.append(" AND variants.price:<=").append(maxPrice);
        }

        String searchQuery = queryBuilder.toString();
        log.info("Browse products - query: '{}', sort: {}, limit: {}", searchQuery, sortBy, limit);

        return productService.searchProductsReactive(searchQuery, limit)
                .map(results -> {
                    try {
                        return objectMapper.writeValueAsString(results);
                    } catch (Exception e) {
                        log.error("Error formatting browse results: {}", e.getMessage(), e);
                        return "{\"error\": \"Error formatting browse results\"}";
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error browsing products: {}", e.getMessage(), e);
                    return Mono.just("{\"error\": \"Error browsing products: " + e.getMessage() + "\"}");
                });
    }

    private String escapeQuery(String value) {
        // Wrap in quotes if it contains spaces
        if (value.contains(" ")) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
        return value.replace("\"", "\\\"");
    }
}
