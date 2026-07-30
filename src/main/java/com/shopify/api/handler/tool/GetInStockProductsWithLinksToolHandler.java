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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tool handler for getting in-stock products with clickable links and add-to-cart URLs
 *
 * Designed specifically for blog content creation and marketing materials.
 * Returns only products with available inventory, formatted with:
 * - Product page URLs (clickable links)
 * - Add to cart permalinks for each variant
 * - Price, SKU, and availability information
 *
 * Input schema:
 * {
 *   "query": "search query",          // optional, searches all products if empty
 *   "limit": 20,                      // optional, defaults to 20
 *   "productType": "PLASTIC KITS"     // optional filter by product type
 * }
 *
 * Returns:
 * {
 *   "products": [
 *     {
 *       "title": "Product Name",
 *       "productUrl": "https://hearnshobbies.com/products/...",
 *       "price": "29.99",
 *       "sku": "ABC123",
 *       "inventoryQuantity": 5,
 *       "variants": [
 *         {
 *           "title": "Variant Name",
 *           "price": "29.99",
 *           "sku": "ABC123",
 *           "inventoryQuantity": 5,
 *           "addToCartUrl": "https://hearnshobbies.com/cart/add?id=..."
 *         }
 *       ],
 *       "imageUrl": "https://..."
 *     }
 *   ],
 *   "totalFound": 10,
 *   "inStockCount": 8
 * }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GetInStockProductsWithLinksToolHandler implements ToolHandler {

    private final ProductService productService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${shopify.store.url:https://hearnshobbies.com.au}")
    private String storeUrl;

    @Value("${chatbot.tools.preorder-tags:preorder,pre-order}")
    private String preorderTagsCsv;

    @Override
    public Mono<JsonNode> execute(JsonNode input) {
        log.info("Executing get_in_stock_products_with_links with input: {}", input);

        // Extract parameters
        String query = input.has("query") ? input.get("query").asText() : "";
        int limit = input.has("limit") ? input.get("limit").asInt() : 20;
        String productType = input.has("productType") ? input.get("productType").asText() : null;

        // Search products (always exclude archived)
        return productService.searchProductsReactive(query, limit, false, productType)
                .map(result -> {
                    ObjectNode response = objectMapper.createObjectNode();
                    ArrayNode productsArray = objectMapper.createArrayNode();

                    int totalFound = 0;
                    int inStockCount = 0;

                    // Extract products from GraphQL response
                    if (result.containsKey("products")) {
                        Map<String, Object> productsData = (Map<String, Object>) result.get("products");

                        if (productsData.containsKey("edges")) {
                            List<Map<String, Object>> edges = (List<Map<String, Object>>) productsData.get("edges");
                            totalFound = edges.size();

                            for (Map<String, Object> edge : edges) {
                                Map<String, Object> productNode = (Map<String, Object>) edge.get("node");

                                // Process product and check if in stock
                                ObjectNode productJson = processProduct(productNode);
                                if (productJson != null) {
                                    productsArray.add(productJson);
                                    inStockCount++;
                                }
                            }
                        }
                    }

                    response.set("products", productsArray);
                    response.put("totalFound", totalFound);
                    response.put("inStockCount", inStockCount);
                    response.put("message", String.format(
                        "Found %d in-stock products out of %d total products matching your query",
                        inStockCount, totalFound
                    ));

                    // Combined "add everything at once" Shopify cart permalink: /cart/{variant}:1,{variant}:1,…
                    // 1 of each product's primary in-stock variant.
                    StringBuilder lot = new StringBuilder();
                    int lotItems = 0;
                    for (JsonNode p : productsArray) {
                        if (p.hasNonNull("cartToken")) {
                            if (lot.length() > 0) lot.append(",");
                            lot.append(p.get("cartToken").asText());
                            lotItems++;
                        }
                    }
                    if (lotItems > 0) {
                        response.put("addAllToCartUrl", storeUrl + "/cart/" + lot);
                        response.put("addAllToCartHint", String.format(
                            "When you present a shopping list or recommend multiple products, end with a markdown link so the "
                            + "customer can add the whole lot in one click, e.g. [🛒 Add all %d to cart](%s/cart/%s) — use the "
                            + "addAllToCartUrl value. If you recommend only some items, build the link yourself as %s/cart/ "
                            + "followed by each chosen product's cartToken joined by commas.",
                            lotItems, storeUrl, lot, storeUrl));
                    }

                    log.info("Returning {} in-stock products out of {} total", inStockCount, totalFound);
                    return (JsonNode) response;
                })
                .doOnSuccess(result -> log.info("Get in-stock products completed successfully"))
                .onErrorResume(error -> {
                    log.error("Get in-stock products failed: {}", error.getMessage());
                    ObjectNode errorResult = objectMapper.createObjectNode();
                    errorResult.put("error", error.getMessage());
                    errorResult.put("success", false);
                    return Mono.just((JsonNode) errorResult);
                });
    }

    /**
     * Process a product node and return formatted product data with links
     * Returns null if product has no inventory
     */
    private ObjectNode processProduct(Map<String, Object> productNode) {
        try {
            // Extract product fields
            String title = (String) productNode.get("title");
            String handle = (String) productNode.get("handle");
            String onlineStoreUrl = (String) productNode.get("onlineStoreUrl");
            String vendor = (String) productNode.get("vendor");
            String productType = (String) productNode.get("productType");

            // Preorder-tagged products are purchasable even at zero inventory —
            // include them (with cart links) instead of filtering them out.
            boolean isPreorder = PreorderTagUtil.isPreorder(
                    (List<String>) productNode.get("tags"),
                    PreorderTagUtil.parsePreorderTags(preorderTagsCsv));

            // Process variants to check inventory
            List<Map<String, Object>> variantEdges = new ArrayList<>();
            if (productNode.containsKey("variants")) {
                Map<String, Object> variantsData = (Map<String, Object>) productNode.get("variants");
                if (variantsData.containsKey("edges")) {
                    variantEdges = (List<Map<String, Object>>) variantsData.get("edges");
                }
            }

            // Check if ANY variant has inventory (preorder products always qualify)
            boolean hasInventory = false;
            String primaryVariantId = null; // first purchasable variant → represents this product in "add all to cart"
            ArrayNode variantsArray = objectMapper.createArrayNode();

            for (Map<String, Object> variantEdge : variantEdges) {
                Map<String, Object> variantNode = (Map<String, Object>) variantEdge.get("node");

                // Get inventory quantity (may be null)
                Object inventoryObj = variantNode.get("inventoryQuantity");
                Integer inventoryQuantity = inventoryObj != null ? ((Number) inventoryObj).intValue() : 0;

                // Include variants with inventory > 0, or any variant of a preorder product
                if (isPreorder || (inventoryQuantity != null && inventoryQuantity > 0)) {
                    hasInventory = true;

                    ObjectNode variantJson = objectMapper.createObjectNode();
                    String variantId = (String) variantNode.get("id");
                    String variantTitle = (String) variantNode.get("title");
                    String sku = (String) variantNode.get("sku");
                    String price = (String) variantNode.get("price");

                    // Extract numeric variant ID from GraphQL ID (gid://shopify/ProductVariant/12345)
                    String numericVariantId = extractNumericId(variantId);
                    if (primaryVariantId == null && !numericVariantId.isEmpty()) {
                        primaryVariantId = numericVariantId;
                    }

                    variantJson.put("title", variantTitle);
                    variantJson.put("sku", sku != null ? sku : "");
                    variantJson.put("price", price);
                    variantJson.put("inventoryQuantity", inventoryQuantity);
                    if (isPreorder) {
                        variantJson.put("preorder", true);
                    }

                    // Generate add to cart URL
                    String addToCartUrl = generateAddToCartUrl(numericVariantId);
                    variantJson.put("addToCartUrl", addToCartUrl);

                    variantsArray.add(variantJson);
                }
            }

            // If no variants have inventory, skip this product
            if (!hasInventory) {
                return null;
            }

            // Build product response
            ObjectNode productJson = objectMapper.createObjectNode();
            productJson.put("title", title);
            productJson.put("productUrl", onlineStoreUrl != null ? onlineStoreUrl : storeUrl + "/products/" + handle);
            productJson.put("vendor", vendor != null ? vendor : "");
            productJson.put("productType", productType != null ? productType : "");
            if (isPreorder) {
                productJson.put("isPreorder", true);
                productJson.put("preorderMessage",
                        "Available to preorder now — present enthusiastically with the Add to Cart link; do NOT say out of stock.");
            }
            productJson.set("variants", variantsArray);

            // Cart token for the combined "add all to cart" permalink (1 of this product's primary variant).
            if (primaryVariantId != null && !primaryVariantId.isEmpty()) {
                productJson.put("cartToken", primaryVariantId + ":1");
            }

            // Add primary image if available
            if (productNode.containsKey("images")) {
                Map<String, Object> imagesData = (Map<String, Object>) productNode.get("images");
                if (imagesData.containsKey("edges")) {
                    List<Map<String, Object>> imageEdges = (List<Map<String, Object>>) imagesData.get("edges");
                    if (!imageEdges.isEmpty()) {
                        Map<String, Object> imageNode = (Map<String, Object>) imageEdges.get(0).get("node");
                        String imageUrl = (String) imageNode.get("url");
                        productJson.put("imageUrl", imageUrl);
                    }
                }
            }

            return productJson;

        } catch (Exception e) {
            log.error("Error processing product: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extract numeric ID from Shopify GraphQL ID format
     * Example: "gid://shopify/ProductVariant/12345" -> "12345"
     */
    private String extractNumericId(String graphqlId) {
        if (graphqlId == null) {
            return "";
        }
        String[] parts = graphqlId.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : "";
    }

    /**
     * Generate add to cart URL for a variant
     * Format: https://hearnshobbies.com.au/cart/add?id=VARIANT_ID
     */
    private String generateAddToCartUrl(String variantId) {
        return storeUrl + "/cart/add?id=" + variantId;
    }

    @Override
    public boolean validateInput(JsonNode input) {
        // All fields are optional, so always valid
        return true;
    }
}
