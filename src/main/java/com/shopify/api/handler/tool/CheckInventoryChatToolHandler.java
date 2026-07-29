package com.shopify.api.handler.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.service.ProductService;
import com.shopify.api.util.PreorderTagUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Chat tool handler for checking product stock availability.
 * Returns inventory quantities with urgency cues for conversion ("Only 3 left!").
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckInventoryChatToolHandler implements ChatToolHandler {

    private final ProductService productService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${chatbot.tools.low-stock-threshold:5}")
    private int lowStockThreshold;

    @Value("${chatbot.tools.preorder-tags:preorder,pre-order}")
    private String preorderTagsCsv;

    @Override
    public String getToolName() {
        return "check_inventory";
    }

    @Override
    public String getToolDescription() {
        return "Check stock availability for a product. Returns inventory quantity and stock status " +
                "(In Stock, Low Stock, Out of Stock). Use when customers ask about availability, " +
                "whether something is in stock, or how many are left.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode queryProp = objectMapper.createObjectNode();
        queryProp.put("type", "string");
        queryProp.put("description", "Product name or search term to check inventory for");
        properties.set("query", queryProp);

        ObjectNode skuProp = objectMapper.createObjectNode();
        skuProp.put("type", "string");
        skuProp.put("description", "Optional: specific SKU to check");
        properties.set("sku", skuProp);

        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("query");
        schema.set("required", required);

        return schema;
    }

    @Override
    public Mono<String> execute(JsonNode input) {
        String query = input.get("query").asText();
        String sku = input.has("sku") ? input.get("sku").asText() : null;

        log.info("Checking inventory for query: '{}', sku: '{}'", query, sku);

        String searchQuery = sku != null ? "sku:" + sku : query;

        return productService.searchProductsReactive(searchQuery, 5)
                .map(results -> formatInventoryResults(results, query))
                .onErrorResume(e -> {
                    log.error("Error checking inventory: {}", e.getMessage(), e);
                    return Mono.just("{\"error\": \"Error checking inventory: " + e.getMessage() + "\"}");
                });
    }

    private String formatInventoryResults(Map<String, Object> results, String query) {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            ArrayNode products = objectMapper.createArrayNode();

            if (results.containsKey("products")) {
                Map<String, Object> productsData = (Map<String, Object>) results.get("products");
                if (productsData.containsKey("edges")) {
                    List<Map<String, Object>> edges = (List<Map<String, Object>>) productsData.get("edges");

                    for (Map<String, Object> edge : edges) {
                        Map<String, Object> node = (Map<String, Object>) edge.get("node");
                        ObjectNode productInfo = formatProductInventory(node);
                        if (productInfo != null) {
                            products.add(productInfo);
                        }
                    }
                }
            }

            response.set("products", products);
            response.put("query", query);
            response.put("resultCount", products.size());

            if (products.size() == 0) {
                response.put("message", "No products found matching '" + query + "'");
            }

            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Error formatting inventory results: {}", e.getMessage(), e);
            return "{\"error\": \"Error formatting inventory results\"}";
        }
    }

    @SuppressWarnings("unchecked")
    private ObjectNode formatProductInventory(Map<String, Object> productNode) {
        try {
            ObjectNode product = objectMapper.createObjectNode();
            product.put("title", (String) productNode.get("title"));
            product.put("handle", (String) productNode.get("handle"));

            String onlineStoreUrl = (String) productNode.get("onlineStoreUrl");
            if (onlineStoreUrl != null) {
                product.put("productUrl", onlineStoreUrl);
            }

            // Preorder-tagged products are purchasable even at zero inventory —
            // treat them as a sellable PREORDER state, not OUT_OF_STOCK.
            boolean isPreorder = PreorderTagUtil.isPreorder(
                    (List<String>) productNode.get("tags"),
                    PreorderTagUtil.parsePreorderTags(preorderTagsCsv));

            ArrayNode variants = objectMapper.createArrayNode();
            int totalInventory = 0;

            if (productNode.containsKey("variants")) {
                Map<String, Object> variantsData = (Map<String, Object>) productNode.get("variants");
                if (variantsData.containsKey("edges")) {
                    List<Map<String, Object>> variantEdges = (List<Map<String, Object>>) variantsData.get("edges");

                    for (Map<String, Object> variantEdge : variantEdges) {
                        Map<String, Object> variantNode = (Map<String, Object>) variantEdge.get("node");

                        ObjectNode variant = objectMapper.createObjectNode();
                        variant.put("title", (String) variantNode.get("title"));
                        variant.put("sku", variantNode.get("sku") != null ? (String) variantNode.get("sku") : "");
                        variant.put("price", (String) variantNode.get("price"));

                        Object invObj = variantNode.get("inventoryQuantity");
                        int qty = invObj != null ? ((Number) invObj).intValue() : 0;
                        variant.put("inventoryQuantity", qty);
                        variant.put("stockStatus", isPreorder ? "PREORDER" : getStockStatus(qty));

                        // Extract variant ID for cart link
                        String variantId = (String) variantNode.get("id");
                        if (variantId != null) {
                            String numericId = variantId.contains("/") ?
                                variantId.substring(variantId.lastIndexOf("/") + 1) : variantId;
                            variant.put("variantId", numericId);
                        }

                        variants.add(variant);
                        totalInventory += qty;
                    }
                }
            }

            product.set("variants", variants);
            product.put("totalInventory", totalInventory);
            product.put("isPreorder", isPreorder);
            product.put("overallStatus", isPreorder ? "PREORDER" : getStockStatus(totalInventory));

            if (isPreorder) {
                product.put("preorderMessage",
                        "Available to preorder now — reserve yours before stock arrives. "
                        + "Present this enthusiastically and include the Add to Cart link; do NOT say it is out of stock.");
            } else if (totalInventory > 0 && totalInventory <= lowStockThreshold) {
                product.put("urgencyMessage", "Only " + totalInventory + " left in stock!");
            }

            return product;
        } catch (Exception e) {
            log.error("Error formatting product inventory: {}", e.getMessage(), e);
            return null;
        }
    }

    private String getStockStatus(int quantity) {
        if (quantity <= 0) return "OUT_OF_STOCK";
        if (quantity <= lowStockThreshold) return "LOW_STOCK";
        return "IN_STOCK";
    }

    @Override
    public boolean validateInput(JsonNode input) {
        return input.has("query");
    }
}
