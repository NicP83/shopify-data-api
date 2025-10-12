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
     * @param startDate Start date (ISO format: 2024-10-01T00:00:00Z)
     * @param endDate End date (ISO format: 2024-10-08T00:00:00Z)
     * @return Orders with sales, freight, and discount data
     */
    public Map<String, Object> getOrdersByDateRange(String startDate, String endDate) {
        logger.info("Fetching orders between {} and {}", startDate, endDate);

        String dateQuery = String.format("created_at:>='%s' AND created_at<'%s'", startDate, endDate);

        String query = String.format("""
            {
              orders(first: 250, query: "%s") {
                edges {
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
                      }
                    }
                    totalDiscountsSet {
                      shopMoney {
                        amount
                      }
                    }
                  }
                }
                pageInfo {
                  hasNextPage
                }
              }
            }
            """, dateQuery);

        GraphQLResponse response = graphQLClient.executeQuery(query);

        if (response.hasErrors()) {
            logger.error("Error fetching orders by date range: {}", response.getErrors());
            throw new RuntimeException("Failed to fetch orders by date range: " +
                    response.getErrors().get(0).getMessage());
        }

        return response.getData();
    }
}
