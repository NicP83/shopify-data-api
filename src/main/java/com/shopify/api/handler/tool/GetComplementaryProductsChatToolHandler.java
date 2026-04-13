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

import java.util.*;

/**
 * Chat tool handler for suggesting complementary products.
 * Uses rule-based mapping of product types to complementary categories.
 * E.g., model kit -> cement, paint, tools, primer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GetComplementaryProductsChatToolHandler implements ChatToolHandler {

    private final ProductService productService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Rule-based complementary product mappings
    private static final Map<String, List<ComplementarySuggestion>> COMPLEMENT_RULES = new LinkedHashMap<>();

    static {
        // Model kits need building supplies
        COMPLEMENT_RULES.put("PLASTIC KITS", List.of(
            new ComplementarySuggestion("CEMENT", "You'll need cement/glue to assemble this kit"),
            new ComplementarySuggestion("PAINT", "Paints to bring your model to life"),
            new ComplementarySuggestion("TOOLS", "Essential tools for clean assembly"),
            new ComplementarySuggestion("PRIMER", "Primer for better paint adhesion")
        ));
        COMPLEMENT_RULES.put("MODEL KIT", List.of(
            new ComplementarySuggestion("CEMENT", "Cement or glue for assembly"),
            new ComplementarySuggestion("PAINT", "Paints for finishing"),
            new ComplementarySuggestion("TOOLS", "Modeling tools")
        ));
        // Gundam kits
        COMPLEMENT_RULES.put("GUNDAM", List.of(
            new ComplementarySuggestion("MARKER", "Panel lining markers for detail"),
            new ComplementarySuggestion("TOOLS", "Nippers and files for clean cuts"),
            new ComplementarySuggestion("TOPCOAT", "Top coat for a professional finish"),
            new ComplementarySuggestion("PAINT", "Paints if you want to customize")
        ));
        // Paints need application tools
        COMPLEMENT_RULES.put("PAINT", List.of(
            new ComplementarySuggestion("BRUSH", "Brushes for application"),
            new ComplementarySuggestion("THINNER", "Thinners for the right consistency"),
            new ComplementarySuggestion("PRIMER", "Primer for better adhesion")
        ));
        // Airbrush needs accessories
        COMPLEMENT_RULES.put("AIRBRUSH", List.of(
            new ComplementarySuggestion("COMPRESSOR", "An airbrush compressor to power it"),
            new ComplementarySuggestion("PAINT", "Airbrush-ready paints"),
            new ComplementarySuggestion("CLEANING", "Airbrush cleaning supplies")
        ));
        // RC models
        COMPLEMENT_RULES.put("RC", List.of(
            new ComplementarySuggestion("BATTERY", "Batteries for power"),
            new ComplementarySuggestion("CHARGER", "Battery charger"),
            new ComplementarySuggestion("PARTS", "Spare parts and upgrades")
        ));
        // Board games
        COMPLEMENT_RULES.put("BOARD GAME", List.of(
            new ComplementarySuggestion("CARD SLEEVES", "Card sleeves for protection"),
            new ComplementarySuggestion("DICE", "Extra dice sets")
        ));
    }

    @Override
    public String getToolName() {
        return "get_complementary_products";
    }

    @Override
    public String getToolDescription() {
        return "Get complementary product suggestions for a given product. " +
                "Use after a customer selects a product to suggest related items they might need. " +
                "For example, if they're buying a model kit, suggest cement, paints, and tools.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode titleProp = objectMapper.createObjectNode();
        titleProp.put("type", "string");
        titleProp.put("description", "Title of the product to find complements for");
        properties.set("product_title", titleProp);

        ObjectNode typeProp = objectMapper.createObjectNode();
        typeProp.put("type", "string");
        typeProp.put("description", "Product type/category (e.g., 'PLASTIC KITS', 'PAINT', 'AIRBRUSH')");
        properties.set("product_type", typeProp);

        ObjectNode vendorProp = objectMapper.createObjectNode();
        vendorProp.put("type", "string");
        vendorProp.put("description", "Product vendor/brand for brand-matched suggestions");
        properties.set("vendor", vendorProp);

        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("product_title");
        schema.set("required", required);

        return schema;
    }

    @Override
    public Mono<String> execute(JsonNode input) {
        String productTitle = input.get("product_title").asText();
        String productType = input.has("product_type") ? input.get("product_type").asText() : "";
        String vendor = input.has("vendor") ? input.get("vendor").asText() : "";

        log.info("Finding complementary products for: '{}', type: '{}', vendor: '{}'",
            productTitle, productType, vendor);

        // Find matching complement rules
        List<ComplementarySuggestion> suggestions = findSuggestions(productType, productTitle);

        if (suggestions.isEmpty()) {
            return Mono.just("{\"suggestions\": [], \"message\": \"No specific complementary suggestions for this product type.\"}");
        }

        // Search for each complementary category
        List<Mono<ObjectNode>> searchMonos = new ArrayList<>();
        for (ComplementarySuggestion suggestion : suggestions) {
            String searchQuery = buildSearchQuery(suggestion.category, vendor);
            Mono<ObjectNode> searchMono = productService.searchProductsReactive(searchQuery, 3)
                .map(results -> {
                    ObjectNode suggestionNode = objectMapper.createObjectNode();
                    suggestionNode.put("category", suggestion.category);
                    suggestionNode.put("reason", suggestion.reason);
                    suggestionNode.set("products", objectMapper.valueToTree(results));
                    return suggestionNode;
                })
                .onErrorResume(e -> {
                    log.warn("Error searching for complementary {}: {}", suggestion.category, e.getMessage());
                    ObjectNode errorNode = objectMapper.createObjectNode();
                    errorNode.put("category", suggestion.category);
                    errorNode.put("reason", suggestion.reason);
                    errorNode.put("error", "Could not search for this category");
                    return Mono.just(errorNode);
                });
            searchMonos.add(searchMono);
        }

        return Mono.zip(searchMonos, results -> {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("forProduct", productTitle);
            ArrayNode suggestionsArray = objectMapper.createArrayNode();
            for (Object result : results) {
                suggestionsArray.add((ObjectNode) result);
            }
            response.set("suggestions", suggestionsArray);
            response.put("message", "Here are complementary products that go well with " + productTitle);
            try {
                return objectMapper.writeValueAsString(response);
            } catch (Exception e) {
                return "{\"error\": \"Error formatting suggestions\"}";
            }
        });
    }

    private List<ComplementarySuggestion> findSuggestions(String productType, String title) {
        // Try exact product type match first
        String upperType = productType.toUpperCase();
        for (Map.Entry<String, List<ComplementarySuggestion>> entry : COMPLEMENT_RULES.entrySet()) {
            if (upperType.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Try matching against product title
        String upperTitle = title.toUpperCase();
        for (Map.Entry<String, List<ComplementarySuggestion>> entry : COMPLEMENT_RULES.entrySet()) {
            if (upperTitle.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Default suggestions for any product
        return List.of(
            new ComplementarySuggestion("TOOLS", "Essential hobby tools"),
            new ComplementarySuggestion("ACCESSORIES", "Related accessories")
        );
    }

    private String buildSearchQuery(String category, String vendor) {
        // If vendor is known, try to suggest same-brand products
        if (vendor != null && !vendor.isEmpty()) {
            return "product_type:" + category + " AND vendor:" + vendor;
        }
        return "product_type:" + category;
    }

    @Override
    public boolean validateInput(JsonNode input) {
        return input.has("product_title") && !input.get("product_title").asText().trim().isEmpty();
    }

    private record ComplementarySuggestion(String category, String reason) {}
}
