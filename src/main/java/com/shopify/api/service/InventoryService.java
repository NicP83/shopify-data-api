package com.shopify.api.service;

import com.shopify.api.client.ShopifyGraphQLClient;
import com.shopify.api.model.GraphQLResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for managing Shopify inventory
 * Provides methods to fetch inventory levels and locations
 */
@Service
public class InventoryService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);

    private final ShopifyGraphQLClient graphQLClient;

    public InventoryService(ShopifyGraphQLClient graphQLClient) {
        this.graphQLClient = graphQLClient;
    }

    /**
     * Get inventory levels for products
     * @param first Number of products to fetch
     * @return Products with inventory information
     */
    public Map<String, Object> getInventory(int first) {
        logger.info("Fetching inventory for {} products", first);

        String query = String.format("""
            {
              products(first: %d) {
                edges {
                  node {
                    id
                    title
                    status
                    variants(first: 100) {
                      edges {
                        node {
                          id
                          title
                          sku
                          inventoryQuantity
                          inventoryItem {
                            id
                            tracked
                            inventoryLevels(first: 10) {
                              edges {
                                node {
                                  id
                                  quantities(names: ["available"]) {
                                    name
                                    quantity
                                  }
                                  location {
                                    id
                                    name
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """, Math.min(first, 250));

        GraphQLResponse response = graphQLClient.executeQuery(query);

        if (response.hasErrors()) {
            logger.error("Error fetching inventory: {}", response.getErrors());
            throw new RuntimeException("Failed to fetch inventory: " +
                    response.getErrors().get(0).getMessage());
        }

        return response.getData();
    }

    /**
     * Get inventory for a specific product by ID
     * @param productId The Shopify product ID
     * @return Product inventory details
     */
    public Map<String, Object> getInventoryByProductId(String productId) {
        logger.info("Fetching inventory for product: {}", productId);

        String query = String.format("""
            {
              product(id: "%s") {
                id
                title
                variants(first: 100) {
                  edges {
                    node {
                      id
                      title
                      sku
                      inventoryQuantity
                      inventoryItem {
                        id
                        tracked
                        inventoryLevels(first: 10) {
                          edges {
                            node {
                              id
                              quantities(names: ["available", "incoming"]) {
                                name
                                quantity
                              }
                              location {
                                id
                                name
                                address {
                                  city
                                  province
                                  country
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """, productId);

        GraphQLResponse response = graphQLClient.executeQuery(query);

        if (response.hasErrors()) {
            logger.error("Error fetching inventory for product {}: {}", productId, response.getErrors());
            throw new RuntimeException("Failed to fetch product inventory: " +
                    response.getErrors().get(0).getMessage());
        }

        return response.getData();
    }

    /**
     * Get all inventory locations
     * @return List of inventory locations
     */
    public Map<String, Object> getLocations() {
        logger.info("Fetching inventory locations");

        String query = """
            {
              locations(first: 50) {
                edges {
                  node {
                    id
                    name
                    isActive
                    address {
                      address1
                      address2
                      city
                      province
                      country
                      zip
                    }
                  }
                }
              }
            }
            """;

        GraphQLResponse response = graphQLClient.executeQuery(query);

        if (response.hasErrors()) {
            logger.error("Error fetching locations: {}", response.getErrors());
            throw new RuntimeException("Failed to fetch locations: " +
                    response.getErrors().get(0).getMessage());
        }

        return response.getData();
    }
}
