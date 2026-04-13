package com.shopify.api.handler.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Chat tool handler for looking up order status.
 * Requires BOTH order number AND email for security (prevents unauthorized lookups).
 * Only exposes non-sensitive fields (no addresses or payment details).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderLookupChatToolHandler implements ChatToolHandler {

    private final OrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getToolName() {
        return "lookup_order";
    }

    @Override
    public String getToolDescription() {
        return "Look up order status for a customer. Requires both order number (e.g., #1234) AND " +
                "customer email for security verification. Returns order status, total, date, and items. " +
                "Use when a customer asks about their order status or tracking.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode orderNumberProp = objectMapper.createObjectNode();
        orderNumberProp.put("type", "string");
        orderNumberProp.put("description", "The order number (e.g., '1234' or '#1234')");
        properties.set("order_number", orderNumberProp);

        ObjectNode emailProp = objectMapper.createObjectNode();
        emailProp.put("type", "string");
        emailProp.put("description", "Customer email address for verification");
        properties.set("email", emailProp);

        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("order_number");
        required.add("email");
        schema.set("required", required);

        return schema;
    }

    @Override
    public Mono<String> execute(JsonNode input) {
        String orderNumber = input.get("order_number").asText().replace("#", "").trim();
        String email = input.get("email").asText().trim().toLowerCase();

        log.info("Looking up order #{} for email {}", orderNumber, email);

        return Mono.fromCallable(() -> {
            // Search by order name
            Map<String, Object> results = orderService.searchOrders("name:#" + orderNumber, 5);
            return formatOrderResults(results, orderNumber, email);
        }).onErrorResume(e -> {
            log.error("Error looking up order: {}", e.getMessage(), e);
            return Mono.just("{\"error\": \"Unable to look up order. Please try again or contact support.\"}");
        });
    }

    @SuppressWarnings("unchecked")
    private String formatOrderResults(Map<String, Object> results, String orderNumber, String email) {
        try {
            ObjectNode response = objectMapper.createObjectNode();

            if (!results.containsKey("orders")) {
                response.put("found", false);
                response.put("message", "Order #" + orderNumber + " not found. Please check the order number and try again.");
                return objectMapper.writeValueAsString(response);
            }

            Map<String, Object> ordersData = (Map<String, Object>) results.get("orders");
            List<Map<String, Object>> edges = (List<Map<String, Object>>) ordersData.get("edges");

            if (edges == null || edges.isEmpty()) {
                response.put("found", false);
                response.put("message", "Order #" + orderNumber + " not found. Please check the order number and try again.");
                return objectMapper.writeValueAsString(response);
            }

            // Find the order that matches both order number and email
            for (Map<String, Object> edge : edges) {
                Map<String, Object> orderNode = (Map<String, Object>) edge.get("node");

                // Verify email matches for security
                String orderEmail = (String) orderNode.get("email");
                if (orderEmail != null && orderEmail.toLowerCase().equals(email)) {
                    response.put("found", true);
                    response.put("orderNumber", "#" + orderNumber);
                    response.put("email", email);

                    // Safe fields only — no addresses, no payment
                    response.put("financialStatus", friendlyStatus((String) orderNode.get("displayFinancialStatus")));
                    response.put("fulfillmentStatus", friendlyStatus((String) orderNode.get("displayFulfillmentStatus")));
                    response.put("total", (String) orderNode.get("totalPrice"));
                    response.put("currency", (String) orderNode.get("currencyCode"));
                    response.put("createdAt", (String) orderNode.get("createdAt"));

                    // Customer first name for personalization (no last name for privacy)
                    if (orderNode.containsKey("customer") && orderNode.get("customer") != null) {
                        Map<String, Object> customer = (Map<String, Object>) orderNode.get("customer");
                        String firstName = (String) customer.get("firstName");
                        if (firstName != null) {
                            response.put("customerFirstName", firstName);
                        }
                    }

                    return objectMapper.writeValueAsString(response);
                }
            }

            // Email didn't match — don't reveal that the order exists
            response.put("found", false);
            response.put("message", "Could not find an order matching that order number and email combination. " +
                    "Please check both and try again.");
            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            log.error("Error formatting order results: {}", e.getMessage(), e);
            return "{\"error\": \"Error processing order lookup\"}";
        }
    }

    private String friendlyStatus(String status) {
        if (status == null) return "Unknown";
        return switch (status) {
            case "PAID" -> "Paid";
            case "PARTIALLY_PAID" -> "Partially Paid";
            case "PENDING" -> "Payment Pending";
            case "REFUNDED" -> "Refunded";
            case "PARTIALLY_REFUNDED" -> "Partially Refunded";
            case "VOIDED" -> "Voided";
            case "FULFILLED" -> "Shipped";
            case "UNFULFILLED" -> "Processing";
            case "PARTIALLY_FULFILLED" -> "Partially Shipped";
            case "IN_PROGRESS" -> "In Progress";
            default -> status.replace("_", " ");
        };
    }

    @Override
    public boolean validateInput(JsonNode input) {
        if (!input.has("order_number") || !input.has("email")) return false;
        String orderNum = input.get("order_number").asText().trim();
        String email = input.get("email").asText().trim();
        return !orderNum.isEmpty() && !email.isEmpty() && email.contains("@");
    }
}
