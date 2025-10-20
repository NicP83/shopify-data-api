package com.shopify.api.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Tool handler for searching products in Shopify
 *
 * Input schema:
 * {
 *   "query": "search query",
 *   "first": 20,  // optional, defaults to 20
 *   "includeArchived": false,  // optional, defaults to false
 *   "productType": "PLASTIC KITS"  // optional filter
 * }
 *
 * Returns:
 * {
 *   "products": [...],
 *   "totalCount": 123,
 *   "pageInfo": {...}
 * }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductSearchToolHandler implements ToolHandler {

    private final ProductService productService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<JsonNode> execute(JsonNode input) {
        log.info("Executing product search with input: {}", input);

        // Extract parameters from input
        String query = input.has("query") ? input.get("query").asText() : "";
        int first = input.has("first") ? input.get("first").asInt() : 20;
        boolean includeArchived = input.has("includeArchived") && input.get("includeArchived").asBoolean();
        String productType = input.has("productType") ? input.get("productType").asText() : null;

        // Execute search
        return productService.searchProductsReactive(query, first, includeArchived, productType)
                .map(result -> {
                    // Convert Map<String, Object> to JsonNode
                    return (JsonNode) objectMapper.valueToTree(result);
                })
                .doOnSuccess(result -> log.info("Product search completed successfully"))
                .onErrorResume(error -> {
                    log.error("Product search failed: {}", error.getMessage());
                    ObjectNode errorResult = objectMapper.createObjectNode();
                    errorResult.put("error", error.getMessage());
                    errorResult.put("success", false);
                    return Mono.just((JsonNode) errorResult);
                });
    }

    @Override
    public boolean validateInput(JsonNode input) {
        // Query is required
        if (!input.has("query")) {
            log.warn("Product search input missing required 'query' field");
            return false;
        }
        return true;
    }
}
