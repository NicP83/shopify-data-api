package com.shopify.api.handler.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Tool handler for getting products from Shopify
 *
 * Input schema:
 * {
 *   "limit": 20  // optional, defaults to 20
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
public class GetProductsToolHandler implements ToolHandler {

    private final ProductService productService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<JsonNode> execute(JsonNode input) {
        log.info("Executing get_products with input: {}", input);

        // Extract limit parameter (default to 20)
        int limit = input.has("limit") ? input.get("limit").asInt() : 20;

        // Use empty search query to get all products
        return productService.searchProductsReactive("", limit, false, null)
                .map(result -> (JsonNode) objectMapper.valueToTree(result))
                .doOnSuccess(result -> log.info("Get products completed successfully"))
                .onErrorResume(error -> {
                    log.error("Get products failed: {}", error.getMessage());
                    ObjectNode errorResult = objectMapper.createObjectNode();
                    errorResult.put("error", error.getMessage());
                    errorResult.put("success", false);
                    return Mono.just((JsonNode) errorResult);
                });
    }

    @Override
    public boolean validateInput(JsonNode input) {
        // Limit is optional, so always valid
        return true;
    }
}
