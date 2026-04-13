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

import java.util.ArrayList;
import java.util.List;

/**
 * Chat tool handler for comparing products side by side.
 * Useful when customers are deciding between similar items.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompareProductsChatToolHandler implements ChatToolHandler {

    private final ProductService productService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getToolName() {
        return "compare_products";
    }

    @Override
    public String getToolDescription() {
        return "Compare two or three products side by side. Searches for each product and returns " +
                "a comparison including title, price, vendor, product type, and availability. " +
                "Use when a customer is deciding between similar items.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode queriesProp = objectMapper.createObjectNode();
        queriesProp.put("type", "array");
        ObjectNode itemSchema = objectMapper.createObjectNode();
        itemSchema.put("type", "string");
        queriesProp.set("items", itemSchema);
        queriesProp.put("description", "List of 2-3 product search queries to compare");
        queriesProp.put("minItems", 2);
        queriesProp.put("maxItems", 3);
        properties.set("product_queries", queriesProp);

        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("product_queries");
        schema.set("required", required);

        return schema;
    }

    @Override
    public Mono<String> execute(JsonNode input) {
        JsonNode queries = input.get("product_queries");
        if (!queries.isArray() || queries.size() < 2) {
            return Mono.just("{\"error\": \"Please provide at least 2 products to compare\"}");
        }

        List<Mono<ObjectNode>> searchMonos = new ArrayList<>();
        for (JsonNode query : queries) {
            String searchTerm = query.asText();
            Mono<ObjectNode> searchMono = productService.searchProductsReactive(searchTerm, 1)
                .map(results -> {
                    ObjectNode product = objectMapper.createObjectNode();
                    product.put("searchedFor", searchTerm);
                    product.set("result", objectMapper.valueToTree(results));
                    return product;
                })
                .onErrorResume(e -> {
                    ObjectNode error = objectMapper.createObjectNode();
                    error.put("searchedFor", searchTerm);
                    error.put("error", "Could not find: " + searchTerm);
                    return Mono.just(error);
                });
            searchMonos.add(searchMono);
        }

        return Mono.zip(searchMonos, results -> {
            ObjectNode response = objectMapper.createObjectNode();
            ArrayNode comparisons = objectMapper.createArrayNode();
            for (Object result : results) {
                comparisons.add((ObjectNode) result);
            }
            response.set("products", comparisons);
            response.put("message", "Here is a side-by-side comparison of " + comparisons.size() + " products");
            try {
                return objectMapper.writeValueAsString(response);
            } catch (Exception e) {
                return "{\"error\": \"Error formatting comparison\"}";
            }
        });
    }

    @Override
    public boolean validateInput(JsonNode input) {
        return input.has("product_queries") && input.get("product_queries").isArray()
                && input.get("product_queries").size() >= 2;
    }
}
