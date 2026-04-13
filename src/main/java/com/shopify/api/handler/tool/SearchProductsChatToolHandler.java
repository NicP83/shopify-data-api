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
import reactor.core.publisher.Mono;

/**
 * Chat tool handler for searching products in the Shopify catalog.
 * This replaces the hardcoded search_products logic in ChatAgentService.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchProductsChatToolHandler implements ChatToolHandler {

    private final ProductService productService;
    private final ChatbotConfigService chatbotConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getToolName() {
        return "search_products";
    }

    @Override
    public String getToolDescription() {
        return "Search the product catalog to find items matching a query. " +
                "Use this to find specific products, check availability, get prices, or browse categories. " +
                "Returns product details including title, description, price, SKU, variants, image URL, and onlineStoreUrl for product page links.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();
        ObjectNode queryProp = objectMapper.createObjectNode();
        queryProp.put("type", "string");
        queryProp.put("description", "Search query to find products (searches title, description, tags, vendor)");
        properties.set("query", queryProp);

        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("query");
        schema.set("required", required);

        return schema;
    }

    @Override
    public Mono<String> execute(JsonNode input) {
        String query = input.get("query").asText();
        int maxResults = chatbotConfigService.getConfig().getMaxSearchResults();

        log.info("Search products - Query: '{}', Max Results: {}", query, maxResults);

        return productService.searchProductsReactive(query, maxResults)
                .map(results -> {
                    try {
                        String jsonResult = objectMapper.writeValueAsString(results);
                        log.debug("Returning {} characters of JSON to Claude", jsonResult.length());
                        return jsonResult;
                    } catch (Exception e) {
                        log.error("Error formatting search results: {}", e.getMessage(), e);
                        return "{\"error\": \"Error formatting results: " + e.getMessage() + "\"}";
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error executing product search: {}", e.getMessage(), e);
                    return Mono.just("{\"error\": \"Error executing search: " + e.getMessage() + "\"}");
                });
    }

    @Override
    public boolean isEnabled(ChatbotConfig config) {
        return config.isEnableProductSearch();
    }

    @Override
    public boolean validateInput(JsonNode input) {
        return input.has("query");
    }
}
