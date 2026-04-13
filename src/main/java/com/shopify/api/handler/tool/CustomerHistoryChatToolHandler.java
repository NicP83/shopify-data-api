package com.shopify.api.handler.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Chat tool handler for looking up customer purchase history.
 * Only activates when the customer self-identifies (provides their email).
 * Used for personalization: "Welcome back! You bought X last time."
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerHistoryChatToolHandler implements ChatToolHandler {

    private final CustomerService customerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getToolName() {
        return "get_customer_history";
    }

    @Override
    public String getToolDescription() {
        return "Look up a customer's purchase history by email address. Only use when the customer " +
                "has voluntarily provided their email. Returns their name, recent orders, and " +
                "purchased products for personalized recommendations. Never look up customers unprompted.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode emailProp = objectMapper.createObjectNode();
        emailProp.put("type", "string");
        emailProp.put("description", "Customer email address (only use if the customer provided it)");
        properties.set("email", emailProp);

        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("email");
        schema.set("required", required);

        return schema;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<String> execute(JsonNode input) {
        String email = input.get("email").asText().trim().toLowerCase();
        log.info("Looking up customer history for: {}", email);

        return Mono.fromCallable(() -> {
            Map<String, Object> results = customerService.searchCustomers("email:" + email, 1);
            return formatCustomerHistory(results, email);
        }).onErrorResume(e -> {
            log.error("Error looking up customer: {}", e.getMessage(), e);
            return Mono.just("{\"found\": false, \"message\": \"Unable to look up customer history.\"}");
        });
    }

    @SuppressWarnings("unchecked")
    private String formatCustomerHistory(Map<String, Object> results, String email) {
        try {
            ObjectNode response = objectMapper.createObjectNode();

            if (!results.containsKey("customers")) {
                response.put("found", false);
                response.put("message", "No customer record found for this email.");
                return objectMapper.writeValueAsString(response);
            }

            Map<String, Object> customersData = (Map<String, Object>) results.get("customers");
            List<Map<String, Object>> edges = (List<Map<String, Object>>) customersData.get("edges");

            if (edges == null || edges.isEmpty()) {
                response.put("found", false);
                response.put("message", "No customer record found for this email.");
                return objectMapper.writeValueAsString(response);
            }

            Map<String, Object> customerNode = (Map<String, Object>) edges.get(0).get("node");

            response.put("found", true);

            // Safe customer info (first name only for privacy)
            String firstName = (String) customerNode.get("firstName");
            if (firstName != null) {
                response.put("firstName", firstName);
            }

            // Order count
            if (customerNode.containsKey("ordersCount")) {
                Object count = customerNode.get("ordersCount");
                if (count instanceof Number) {
                    response.put("totalOrders", ((Number) count).intValue());
                }
            }

            // Recent orders
            if (customerNode.containsKey("orders")) {
                Map<String, Object> ordersData = (Map<String, Object>) customerNode.get("orders");
                if (ordersData.containsKey("edges")) {
                    List<Map<String, Object>> orderEdges = (List<Map<String, Object>>) ordersData.get("edges");
                    ArrayNode recentOrders = objectMapper.createArrayNode();

                    for (Map<String, Object> orderEdge : orderEdges) {
                        Map<String, Object> order = (Map<String, Object>) orderEdge.get("node");
                        ObjectNode orderInfo = objectMapper.createObjectNode();
                        orderInfo.put("orderNumber", (String) order.get("name"));
                        orderInfo.put("date", (String) order.get("createdAt"));
                        orderInfo.put("total", (String) order.get("totalPrice"));
                        orderInfo.put("status", (String) order.get("displayFulfillmentStatus"));

                        // Line items for product history
                        if (order.containsKey("lineItems")) {
                            Map<String, Object> lineItemsData = (Map<String, Object>) order.get("lineItems");
                            if (lineItemsData.containsKey("edges")) {
                                List<Map<String, Object>> itemEdges = (List<Map<String, Object>>) lineItemsData.get("edges");
                                ArrayNode items = objectMapper.createArrayNode();
                                for (Map<String, Object> itemEdge : itemEdges) {
                                    Map<String, Object> item = (Map<String, Object>) itemEdge.get("node");
                                    items.add((String) item.get("title"));
                                }
                                orderInfo.set("products", items);
                            }
                        }

                        recentOrders.add(orderInfo);
                    }
                    response.set("recentOrders", recentOrders);
                }
            }

            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Error formatting customer history: {}", e.getMessage(), e);
            return "{\"found\": false, \"message\": \"Error processing customer data.\"}";
        }
    }

    @Override
    public boolean validateInput(JsonNode input) {
        if (!input.has("email")) return false;
        String email = input.get("email").asText().trim();
        return !email.isEmpty() && email.contains("@");
    }
}
