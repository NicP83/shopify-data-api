package com.shopify.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopify.api.client.MCPClient;
import com.shopify.api.client.ShopifyGraphQLClient;
import com.shopify.api.model.GraphQLResponse;
import com.shopify.api.model.OrderLineItem;
import com.shopify.api.model.UnfulfilledOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing order fulfillment
 * Checks Shopify orders against CRS ERP to find orders pending fulfillment
 */
@Service
public class FulfillmentService {

    private static final Logger logger = LoggerFactory.getLogger(FulfillmentService.class);

    private final ShopifyGraphQLClient graphQLClient;
    private final MCPClient mcpClient;
    private final ObjectMapper objectMapper;

    public FulfillmentService(ShopifyGraphQLClient graphQLClient, MCPClient mcpClient) {
        this.graphQLClient = graphQLClient;
        this.mcpClient = mcpClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get all unfulfilled Shopify orders that haven't been processed in CRS
     * Fetches most recent orders from last 3 months and checks each against CRS
     * @return List of unfulfilled orders
     */
    public List<UnfulfilledOrder> getUnfulfilledOrders() {
        logger.info("Fetching recent Shopify orders and checking against CRS");

        try {
            // Step 1: Fetch recent orders from Shopify (sorted newest first)
            List<UnfulfilledOrder> shopifyOrders = fetchRecentShopifyOrders();
            logger.info("Found {} recent orders from Shopify", shopifyOrders.size());

            // Step 2: Check each order against CRS and enrich with sale data
            List<UnfulfilledOrder> ordersToFulfill = new ArrayList<>();
            for (UnfulfilledOrder order : shopifyOrders) {
                try {
                    boolean existsInCRS = checkOrderInCRS(order.getOrderName()).block();
                    if (!existsInCRS) {
                        // Enrich order with CRS sale information for each line item
                        enrichOrderWithCRSSaleData(order);
                        ordersToFulfill.add(order);
                        logger.debug("Order {} not found in CRS, adding to fulfillment list", order.getOrderName());
                    } else {
                        logger.debug("Order {} already exists in CRS, skipping", order.getOrderName());
                    }
                } catch (Exception e) {
                    logger.error("Error checking order {} in CRS: {}", order.getOrderName(), e.getMessage());
                    // If CRS check fails, include the order to be safe
                    ordersToFulfill.add(order);
                }
            }

            logger.info("Found {} orders pending fulfillment in CRS", ordersToFulfill.size());
            return ordersToFulfill;

        } catch (Exception e) {
            logger.error("Error fetching unfulfilled orders: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Fetch recent orders from Shopify (most recent first, last 3 months)
     * Does NOT filter by fulfillment status - checks ALL orders against CRS
     */
    private List<UnfulfilledOrder> fetchRecentShopifyOrders() {
        // Get most recent 250 orders, sorted by creation date (newest first)
        String query = """
            {
              orders(first: 250, reverse: true, sortKey: CREATED_AT) {
                edges {
                  node {
                    id
                    name
                    email
                    phone
                    createdAt
                    displayFulfillmentStatus
                    note
                    subtotalPriceSet {
                      shopMoney {
                        amount
                        currencyCode
                      }
                    }
                    totalPriceSet {
                      shopMoney {
                        amount
                        currencyCode
                      }
                    }
                    totalDiscountsSet {
                      shopMoney {
                        amount
                      }
                    }
                    discountCodes
                    customer {
                      email
                      firstName
                      lastName
                      phone
                    }
                    lineItems(first: 100) {
                      edges {
                        node {
                          id
                          title
                          quantity
                          originalUnitPrice
                          discountedTotalSet {
                            shopMoney {
                              amount
                            }
                          }
                          discountAllocations {
                            allocatedAmountSet {
                              shopMoney {
                                amount
                              }
                            }
                            discountApplication {
                              ... on DiscountCodeApplication {
                                code
                              }
                            }
                          }
                          variant {
                            id
                            title
                            sku
                          }
                        }
                      }
                    }
                    shippingAddress {
                      firstName
                      lastName
                      company
                      address1
                      address2
                      city
                      province
                      country
                      zip
                      phone
                    }
                  }
                }
              }
            }
            """;

        GraphQLResponse response = graphQLClient.executeQuery(query);

        if (response.hasErrors()) {
            logger.error("Error fetching recent orders from Shopify: {}", response.getErrors());
            throw new RuntimeException("Failed to fetch recent orders: " +
                    response.getErrors().get(0).getMessage());
        }

        List<UnfulfilledOrder> allOrders = parseShopifyOrders(response.getData());

        // Filter to only orders from last 3 months
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);
        List<UnfulfilledOrder> recentOrders = allOrders.stream()
                .filter(order -> order.getCreatedAt() != null && order.getCreatedAt().isAfter(threeMonthsAgo))
                .collect(java.util.stream.Collectors.toList());

        logger.info("Filtered to {} orders from last 3 months (from {} total)", recentOrders.size(), allOrders.size());

        return recentOrders;
    }

    /**
     * Parse Shopify GraphQL response into UnfulfilledOrder objects
     */
    private List<UnfulfilledOrder> parseShopifyOrders(Map<String, Object> data) {
        List<UnfulfilledOrder> orders = new ArrayList<>();

        try {
            Map<String, Object> ordersData = (Map<String, Object>) data.get("orders");
            List<Map<String, Object>> edges = (List<Map<String, Object>>) ordersData.get("edges");

            for (Map<String, Object> edge : edges) {
                Map<String, Object> node = (Map<String, Object>) edge.get("node");
                UnfulfilledOrder order = new UnfulfilledOrder();

                // Basic order info
                order.setOrderId((String) node.get("id"));
                order.setOrderName((String) node.get("name"));
                order.setDisplayFulfillmentStatus((String) node.get("displayFulfillmentStatus"));
                order.setNote((String) node.get("note"));

                // Parse created date
                String createdAtStr = (String) node.get("createdAt");
                if (createdAtStr != null) {
                    order.setCreatedAt(ZonedDateTime.parse(createdAtStr).toLocalDateTime());
                }

                // Customer info
                Map<String, Object> customer = (Map<String, Object>) node.get("customer");
                if (customer != null) {
                    String firstName = (String) customer.get("firstName");
                    String lastName = (String) customer.get("lastName");
                    order.setCustomerName((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : ""));
                    order.setCustomerEmail((String) customer.get("email"));
                    order.setCustomerPhone((String) customer.get("phone"));
                } else {
                    order.setCustomerEmail((String) node.get("email"));
                    order.setCustomerPhone((String) node.get("phone"));
                }

                // Price info
                Map<String, Object> subtotalPriceSet = (Map<String, Object>) node.get("subtotalPriceSet");
                if (subtotalPriceSet != null) {
                    Map<String, Object> shopMoney = (Map<String, Object>) subtotalPriceSet.get("shopMoney");
                    if (shopMoney != null) {
                        String amount = (String) shopMoney.get("amount");
                        if (amount != null) {
                            order.setSubtotalPrice(new BigDecimal(amount));
                        }
                    }
                }

                Map<String, Object> totalPriceSet = (Map<String, Object>) node.get("totalPriceSet");
                if (totalPriceSet != null) {
                    Map<String, Object> shopMoney = (Map<String, Object>) totalPriceSet.get("shopMoney");
                    if (shopMoney != null) {
                        String amount = (String) shopMoney.get("amount");
                        if (amount != null) {
                            order.setTotalPrice(new BigDecimal(amount));
                        }
                        order.setCurrencyCode((String) shopMoney.get("currencyCode"));
                    }
                }

                // Discount info
                Map<String, Object> totalDiscountsSet = (Map<String, Object>) node.get("totalDiscountsSet");
                if (totalDiscountsSet != null) {
                    Map<String, Object> shopMoney = (Map<String, Object>) totalDiscountsSet.get("shopMoney");
                    if (shopMoney != null) {
                        String amount = (String) shopMoney.get("amount");
                        if (amount != null) {
                            order.setTotalDiscounts(new BigDecimal(amount));
                        }
                    }
                }

                // Discount codes
                String discountCodes = (String) node.get("discountCodes");
                order.setDiscountCodes(discountCodes);

                // Line items
                Map<String, Object> lineItemsData = (Map<String, Object>) node.get("lineItems");
                if (lineItemsData != null) {
                    List<Map<String, Object>> lineItemEdges = (List<Map<String, Object>>) lineItemsData.get("edges");
                    List<OrderLineItem> lineItems = new ArrayList<>();
                    int totalItems = 0;

                    for (Map<String, Object> lineItemEdge : lineItemEdges) {
                        Map<String, Object> lineItemNode = (Map<String, Object>) lineItemEdge.get("node");
                        OrderLineItem lineItem = new OrderLineItem();

                        lineItem.setLineItemId((String) lineItemNode.get("id"));
                        lineItem.setTitle((String) lineItemNode.get("title"));

                        Integer quantity = (Integer) lineItemNode.get("quantity");
                        lineItem.setQuantity(quantity);
                        totalItems += (quantity != null ? quantity : 0);

                        String priceStr = (String) lineItemNode.get("originalUnitPrice");
                        if (priceStr != null) {
                            lineItem.setPrice(new BigDecimal(priceStr));
                        }

                        // Discounted total price
                        Map<String, Object> discountedTotalSet = (Map<String, Object>) lineItemNode.get("discountedTotalSet");
                        if (discountedTotalSet != null) {
                            Map<String, Object> shopMoney = (Map<String, Object>) discountedTotalSet.get("shopMoney");
                            if (shopMoney != null) {
                                String amount = (String) shopMoney.get("amount");
                                if (amount != null) {
                                    lineItem.setDiscountedTotalPrice(new BigDecimal(amount));
                                }
                            }
                        }

                        // Discount allocations
                        List<Map<String, Object>> discountAllocations = (List<Map<String, Object>>) lineItemNode.get("discountAllocations");
                        if (discountAllocations != null && !discountAllocations.isEmpty()) {
                            StringBuilder discountDesc = new StringBuilder();
                            BigDecimal totalDiscount = BigDecimal.ZERO;

                            for (Map<String, Object> allocation : discountAllocations) {
                                Map<String, Object> allocatedAmountSet = (Map<String, Object>) allocation.get("allocatedAmountSet");
                                if (allocatedAmountSet != null) {
                                    Map<String, Object> shopMoney = (Map<String, Object>) allocatedAmountSet.get("shopMoney");
                                    if (shopMoney != null) {
                                        String amount = (String) shopMoney.get("amount");
                                        if (amount != null) {
                                            totalDiscount = totalDiscount.add(new BigDecimal(amount));
                                        }
                                    }
                                }

                                Map<String, Object> discountApp = (Map<String, Object>) allocation.get("discountApplication");
                                if (discountApp != null && discountApp.containsKey("code")) {
                                    String code = (String) discountApp.get("code");
                                    if (discountDesc.length() > 0) {
                                        discountDesc.append(", ");
                                    }
                                    discountDesc.append(code);
                                }
                            }

                            if (discountDesc.length() > 0) {
                                lineItem.setDiscountAllocations(discountDesc.toString() + " (-$" + totalDiscount.toString() + ")");
                            }
                        }

                        Map<String, Object> variant = (Map<String, Object>) lineItemNode.get("variant");
                        if (variant != null) {
                            lineItem.setSku((String) variant.get("sku"));
                            lineItem.setVariantTitle((String) variant.get("title"));
                        }

                        lineItems.add(lineItem);
                    }

                    order.setLineItems(lineItems);
                    order.setItemCount(totalItems);
                }

                // Shipping address
                Map<String, Object> shippingAddr = (Map<String, Object>) node.get("shippingAddress");
                if (shippingAddr != null) {
                    order.setShippingAddress(formatAddress(shippingAddr));
                }

                orders.add(order);
            }

        } catch (Exception e) {
            logger.error("Error parsing Shopify orders: {}", e.getMessage(), e);
        }

        return orders;
    }

    /**
     * Format shipping address into a readable string
     */
    private String formatAddress(Map<String, Object> address) {
        StringBuilder sb = new StringBuilder();

        String firstName = (String) address.get("firstName");
        String lastName = (String) address.get("lastName");
        if (firstName != null || lastName != null) {
            sb.append(firstName != null ? firstName : "").append(" ").append(lastName != null ? lastName : "").append("\n");
        }

        String company = (String) address.get("company");
        if (company != null && !company.isEmpty()) {
            sb.append(company).append("\n");
        }

        String address1 = (String) address.get("address1");
        if (address1 != null) {
            sb.append(address1).append("\n");
        }

        String address2 = (String) address.get("address2");
        if (address2 != null && !address2.isEmpty()) {
            sb.append(address2).append("\n");
        }

        String city = (String) address.get("city");
        String province = (String) address.get("province");
        String zip = (String) address.get("zip");
        sb.append(city != null ? city : "").append(", ")
          .append(province != null ? province : "").append(" ")
          .append(zip != null ? zip : "").append("\n");

        String country = (String) address.get("country");
        if (country != null) {
            sb.append(country);
        }

        return sb.toString().trim();
    }

    /**
     * Check if an order exists in CRS by querying via MCP
     * @param orderName The Shopify order name (e.g., "#1001")
     * @return true if order exists in CRS, false otherwise
     */
    public Mono<Boolean> checkOrderInCRS(String orderName) {
        if (!mcpClient.isEnabled()) {
            logger.warn("MCP is disabled, cannot check order in CRS");
            return Mono.just(false);
        }

        logger.debug("Checking if order {} exists in CRS", orderName);

        // Query CRS to check if order exists
        String query = String.format(
            "SELECT COUNT(*) as OrderCount FROM InvoiceHeader WHERE ShopifyOrderNum = '%s'",
            orderName.replace("'", "''") // Escape single quotes
        );

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("query", query);

        return mcpClient.callTool("query_database", arguments)
                .map(result -> parseOrderCountResult(result))
                .map(count -> count > 0)
                .onErrorResume(e -> {
                    logger.error("Error checking order {} in CRS: {}", orderName, e.getMessage());
                    return Mono.just(false); // On error, assume not in CRS
                });
    }

    /**
     * Enrich order with CRS sale data for each line item
     * Checks if products were on sale at time of order
     */
    private void enrichOrderWithCRSSaleData(UnfulfilledOrder order) {
        if (!mcpClient.isEnabled()) {
            logger.debug("MCP is disabled, skipping CRS sale data enrichment");
            return;
        }

        for (OrderLineItem lineItem : order.getLineItems()) {
            try {
                String sku = lineItem.getSku();
                if (sku == null || sku.trim().isEmpty()) {
                    continue;
                }

                // Query CRS for product pricing information
                String query = String.format(
                    "SELECT ItemCode, RegularPrice, SalePrice, OnSale FROM ItemMaster WHERE ItemCode = '%s'",
                    sku.replace("'", "''") // Escape single quotes
                );

                Map<String, Object> arguments = new HashMap<>();
                arguments.put("query", query);

                JsonNode result = mcpClient.callTool("query_database", arguments).block();
                if (result != null) {
                    parseCRSSaleData(result, lineItem);
                }
            } catch (Exception e) {
                logger.debug("Could not fetch CRS sale data for SKU {}: {}", lineItem.getSku(), e.getMessage());
                // Continue processing other items even if one fails
            }
        }
    }

    /**
     * Parse CRS sale data from MCP response
     */
    private void parseCRSSaleData(JsonNode result, OrderLineItem lineItem) {
        try {
            if (result.has("content") && result.get("content").isArray() && result.get("content").size() > 0) {
                JsonNode content = result.get("content").get(0);
                if (content.has("text")) {
                    String text = content.get("text").asText();

                    // Extract JSON from text
                    int jsonStart = text.indexOf('{');
                    int jsonEnd = text.lastIndexOf('}');

                    if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                        String jsonStr = text.substring(jsonStart, jsonEnd + 1);
                        JsonNode jsonNode = objectMapper.readTree(jsonStr);

                        // Extract pricing information
                        if (jsonNode.has("RegularPrice")) {
                            double regularPrice = jsonNode.get("RegularPrice").asDouble();
                            lineItem.setCrsRegularPrice(BigDecimal.valueOf(regularPrice));
                        }

                        if (jsonNode.has("SalePrice")) {
                            double salePrice = jsonNode.get("SalePrice").asDouble();
                            lineItem.setCrsSalePrice(BigDecimal.valueOf(salePrice));
                        }

                        if (jsonNode.has("OnSale")) {
                            boolean onSale = jsonNode.get("OnSale").asBoolean();
                            lineItem.setOnSale(onSale);
                        }

                        logger.debug("Enriched SKU {} with CRS data: RegularPrice=${}, SalePrice=${}, OnSale={}",
                                lineItem.getSku(), lineItem.getCrsRegularPrice(),
                                lineItem.getCrsSalePrice(), lineItem.getOnSale());
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error parsing CRS sale data for SKU {}: {}", lineItem.getSku(), e.getMessage());
        }
    }

    /**
     * Parse the COUNT result from CRS query
     */
    private int parseOrderCountResult(JsonNode result) {
        try {
            if (result.has("content") && result.get("content").isArray() && result.get("content").size() > 0) {
                JsonNode content = result.get("content").get(0);
                if (content.has("text")) {
                    String text = content.get("text").asText();

                    // Extract JSON from text
                    int jsonStart = text.indexOf('{');
                    int jsonEnd = text.lastIndexOf('}');

                    if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                        String jsonStr = text.substring(jsonStart, jsonEnd + 1);
                        JsonNode jsonNode = objectMapper.readTree(jsonStr);

                        if (jsonNode.has("OrderCount")) {
                            return jsonNode.get("OrderCount").asInt();
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing order count result: {}", e.getMessage());
        }

        return 0;
    }
}
