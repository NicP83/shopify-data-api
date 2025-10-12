package com.shopify.api.service;

import com.shopify.api.client.ShopifyGraphQLClient;
import com.shopify.api.model.GraphQLResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for managing Shopify orders
 * Provides methods to fetch order data
 */
@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final ShopifyGraphQLClient graphQLClient;

    public OrderService(ShopifyGraphQLClient graphQLClient) {
        this.graphQLClient = graphQLClient;
    }

    /**
     * Get all orders with pagination
     * @param first Number of orders to fetch (max 250)
     * @return List of orders
     */
    public Map<String, Object> getOrders(int first) {
        logger.info("Fetching {} orders from Shopify", first);

        String query = String.format("""
            {
              orders(first: %d) {
                edges {
                  node {
                    id
                    name
                    email
                    createdAt
                    updatedAt
                    closedAt
                    cancelledAt
                    cancelReason
                    confirmed
                    displayFinancialStatus
                    displayFulfillmentStatus
                    subtotalPrice
                    totalPrice
                    totalTax
                    totalShippingPrice
                    currencyCode
                    customer {
                      id
                      email
                      firstName
                      lastName
                    }
                    lineItems(first: 50) {
                      edges {
                        node {
                          id
                          title
                          quantity
                          originalUnitPrice
                          variant {
                            id
                            sku
                          }
                        }
                      }
                    }
                    shippingAddress {
                      address1
                      address2
                      city
                      province
                      country
                      zip
                    }
                  }
                }
                pageInfo {
                  hasNextPage
                  hasPreviousPage
                }
              }
            }
            """, Math.min(first, 250));

        GraphQLResponse response = graphQLClient.executeQuery(query);

        if (response.hasErrors()) {
            logger.error("Error fetching orders: {}", response.getErrors());
            throw new RuntimeException("Failed to fetch orders: " +
                    response.getErrors().get(0).getMessage());
        }

        return response.getData();
    }

    /**
     * Get a single order by ID
     * @param orderId The Shopify order ID
     * @return Order data
     */
    public Map<String, Object> getOrderById(String orderId) {
        logger.info("Fetching order with ID: {}", orderId);

        String query = String.format("""
            {
              order(id: "%s") {
                id
                name
                email
                phone
                createdAt
                updatedAt
                closedAt
                cancelledAt
                cancelReason
                note
                confirmed
                displayFinancialStatus
                displayFulfillmentStatus
                subtotalPrice
                totalPrice
                totalTax
                totalShippingPrice
                totalDiscounts
                currencyCode
                customer {
                  id
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
                      discountedUnitPrice
                      variant {
                        id
                        title
                        sku
                        product {
                          id
                          title
                        }
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
                billingAddress {
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
            """, orderId);

        GraphQLResponse response = graphQLClient.executeQuery(query);

        if (response.hasErrors()) {
            logger.error("Error fetching order {}: {}", orderId, response.getErrors());
            throw new RuntimeException("Failed to fetch order: " +
                    response.getErrors().get(0).getMessage());
        }

        return response.getData();
    }

    /**
     * Search orders by query (e.g., email, name, status)
     * @param searchQuery The search query
     * @param first Number of results
     * @return Search results
     */
    public Map<String, Object> searchOrders(String searchQuery, int first) {
        logger.info("Searching orders with query: {}", searchQuery);

        String query = String.format("""
            {
              orders(first: %d, query: "%s") {
                edges {
                  node {
                    id
                    name
                    email
                    createdAt
                    displayFinancialStatus
                    displayFulfillmentStatus
                    totalPrice
                    currencyCode
                    customer {
                      id
                      email
                      firstName
                      lastName
                    }
                  }
                }
              }
            }
            """, Math.min(first, 250), searchQuery);

        GraphQLResponse response = graphQLClient.executeQuery(query);

        if (response.hasErrors()) {
            logger.error("Error searching orders: {}", response.getErrors());
            throw new RuntimeException("Failed to search orders: " +
                    response.getErrors().get(0).getMessage());
        }

        return response.getData();
    }

    /**
     * Get orders within a date range for analytics
     * Automatically handles pagination to fetch all orders in the date range
     * @param startDate Start date (ISO format: 2024-10-01T00:00:00Z)
     * @param endDate End date (ISO format: 2024-10-08T00:00:00Z)
     * @return Orders with sales, freight, and discount data
     */
    public Map<String, Object> getOrdersByDateRange(String startDate, String endDate) {
        // Use simple date format that Shopify accepts (yyyy-MM-dd)
        // Use >= and <= to be inclusive of both start and end dates
        String dateQuery = String.format("created_at:>=%s AND created_at:<=%s", startDate, endDate);

        logger.info("Fetching orders between {} and {} with query: {}", startDate, endDate, dateQuery);

        List<Map<String, Object>> allEdges = new java.util.ArrayList<>();
        String cursor = null;
        boolean hasNextPage = true;
        int pageCount = 0;

        // Fetch all pages of orders
        while (hasNextPage && pageCount < 20) { // Max 20 pages = 5000 orders safety limit
            pageCount++;
            String cursorParam = cursor != null ? String.format(", after: \"%s\"", cursor) : "";

            String query = String.format("""
                {
                  orders(first: 250, query: "%s"%s) {
                    edges {
                      cursor
                      node {
                        id
                        name
                        createdAt
                        totalPriceSet {
                          shopMoney {
                            amount
                            currencyCode
                          }
                        }
                        totalShippingPriceSet {
                          shopMoney {
                            amount
                            currencyCode
                          }
                        }
                        totalDiscountsSet {
                          shopMoney {
                            amount
                            currencyCode
                          }
                        }
                      }
                    }
                    pageInfo {
                      hasNextPage
                      endCursor
                    }
                  }
                }
                """, dateQuery, cursorParam);

            GraphQLResponse response = graphQLClient.executeQuery(query);

            if (response.hasErrors()) {
                logger.error("Error fetching orders by date range (page {}): {}", pageCount, response.getErrors());
                throw new RuntimeException("Failed to fetch orders by date range: " +
                        response.getErrors().get(0).getMessage());
            }

            Map<String, Object> data = response.getData();
            if (data != null && data.containsKey("orders")) {
                Map<String, Object> ordersResponse = (Map<String, Object>) data.get("orders");
                List<Map<String, Object>> edges = (List<Map<String, Object>>) ordersResponse.get("edges");

                if (edges != null && !edges.isEmpty()) {
                    allEdges.addAll(edges);
                    logger.info("Fetched page {} with {} orders", pageCount, edges.size());
                }

                // Check if there are more pages
                Map<String, Object> pageInfo = (Map<String, Object>) ordersResponse.get("pageInfo");
                if (pageInfo != null) {
                    hasNextPage = (Boolean) pageInfo.getOrDefault("hasNextPage", false);
                    cursor = (String) pageInfo.get("endCursor");
                } else {
                    hasNextPage = false;
                }
            } else {
                hasNextPage = false;
            }
        }

        logger.info("Total orders fetched for date range: {} (across {} pages)", allEdges.size(), pageCount);

        // Return data in same format as before
        Map<String, Object> result = new java.util.HashMap<>();
        Map<String, Object> orders = new java.util.HashMap<>();
        orders.put("edges", allEdges);
        result.put("orders", orders);

        return result;
    }
}
