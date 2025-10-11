package com.shopify.api.service;

import com.shopify.api.client.ShopifyGraphQLClient;
import com.shopify.api.model.GraphQLResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for managing Shopify customers
 * Provides methods to fetch customer data
 */
@Service
public class CustomerService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerService.class);

    private final ShopifyGraphQLClient graphQLClient;

    public CustomerService(ShopifyGraphQLClient graphQLClient) {
        this.graphQLClient = graphQLClient;
    }

    /**
     * Get all customers with pagination
     * @param first Number of customers to fetch (max 250)
     * @return List of customers
     */
    public Map<String, Object> getCustomers(int first) {
        logger.info("Fetching {} customers from Shopify", first);

        String query = String.format("""
            {
              customers(first: %d) {
                edges {
                  node {
                    id
                    email
                    firstName
                    lastName
                    phone
                    createdAt
                    updatedAt
                    state
                    note
                    verifiedEmail
                    taxExempt
                    tags
                    ordersCount
                    totalSpent
                    defaultAddress {
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
            logger.error("Error fetching customers: {}", response.getErrors());
            throw new RuntimeException("Failed to fetch customers: " +
                    response.getErrors().get(0).getMessage());
        }

        return response.getData();
    }

    /**
     * Get a single customer by ID
     * @param customerId The Shopify customer ID
     * @return Customer data with full details including orders
     */
    public Map<String, Object> getCustomerById(String customerId) {
        logger.info("Fetching customer with ID: {}", customerId);

        String query = String.format("""
            {
              customer(id: "%s") {
                id
                email
                firstName
                lastName
                phone
                createdAt
                updatedAt
                state
                note
                verifiedEmail
                taxExempt
                tags
                ordersCount
                totalSpent
                lifetimeDuration
                defaultAddress {
                  id
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
                addresses(first: 10) {
                  edges {
                    node {
                      id
                      address1
                      address2
                      city
                      province
                      country
                      zip
                    }
                  }
                }
                orders(first: 10) {
                  edges {
                    node {
                      id
                      name
                      createdAt
                      displayFinancialStatus
                      displayFulfillmentStatus
                      totalPrice
                    }
                  }
                }
              }
            }
            """, customerId);

        GraphQLResponse response = graphQLClient.executeQuery(query);

        if (response.hasErrors()) {
            logger.error("Error fetching customer {}: {}", customerId, response.getErrors());
            throw new RuntimeException("Failed to fetch customer: " +
                    response.getErrors().get(0).getMessage());
        }

        return response.getData();
    }

    /**
     * Search customers by query (email, name, phone, etc.)
     * @param searchQuery The search query
     * @param first Number of results
     * @return Search results
     */
    public Map<String, Object> searchCustomers(String searchQuery, int first) {
        logger.info("Searching customers with query: {}", searchQuery);

        String query = String.format("""
            {
              customers(first: %d, query: "%s") {
                edges {
                  node {
                    id
                    email
                    firstName
                    lastName
                    phone
                    createdAt
                    ordersCount
                    totalSpent
                    state
                  }
                }
              }
            }
            """, Math.min(first, 250), searchQuery);

        GraphQLResponse response = graphQLClient.executeQuery(query);

        if (response.hasErrors()) {
            logger.error("Error searching customers: {}", response.getErrors());
            throw new RuntimeException("Failed to search customers: " +
                    response.getErrors().get(0).getMessage());
        }

        return response.getData();
    }
}
