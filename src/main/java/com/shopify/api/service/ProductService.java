package com.shopify.api.service;

import com.shopify.api.client.ShopifyGraphQLClient;
import com.shopify.api.model.GraphQLResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for managing Shopify products
 * Provides methods to fetch and manipulate product data
 */
@Service
public class ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    private final ShopifyGraphQLClient graphQLClient;

    public ProductService(ShopifyGraphQLClient graphQLClient) {
        this.graphQLClient = graphQLClient;
    }

    /**
     * Get all products with pagination
     * @param first Number of products to fetch (max 250)
     * @return List of products
     */
    public Map<String, Object> getProducts(int first) {
        logger.info("Fetching {} products from Shopify", first);

        String query = String.format("""
            {
              products(first: %d) {
                edges {
                  node {
                    id
                    title
                    description
                    handle
                    status
                    vendor
                    productType
                    createdAt
                    updatedAt
                    tags
                    variants(first: 10) {
                      edges {
                        node {
                          id
                          title
                          sku
                          price
                          inventoryQuantity
                        }
                      }
                    }
                    images(first: 5) {
                      edges {
                        node {
                          url
                          altText
                        }
                      }
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
            logger.error("Error fetching products: {}", response.getErrors());
            throw new RuntimeException("Failed to fetch products: " +
                    response.getErrors().get(0).getMessage());
        }

        return response.getData();
    }

    /**
     * Get a single product by ID
     * @param productId The Shopify product ID
     * @return Product data
     */
    public Map<String, Object> getProductById(String productId) {
        logger.info("Fetching product with ID: {}", productId);

        String query = String.format("""
            {
              product(id: "%s") {
                id
                title
                description
                descriptionHtml
                handle
                status
                vendor
                productType
                createdAt
                updatedAt
                tags
                options {
                  name
                  values
                }
                variants(first: 100) {
                  edges {
                    node {
                      id
                      title
                      sku
                      price
                      compareAtPrice
                      inventoryQuantity
                      weight
                      weightUnit
                      requiresShipping
                      barcode
                    }
                  }
                }
                images(first: 10) {
                  edges {
                    node {
                      id
                      url
                      altText
                      width
                      height
                    }
                  }
                }
              }
            }
            """, productId);

        GraphQLResponse response = graphQLClient.executeQuery(query);

        if (response.hasErrors()) {
            logger.error("Error fetching product {}: {}", productId, response.getErrors());
            throw new RuntimeException("Failed to fetch product: " +
                    response.getErrors().get(0).getMessage());
        }

        return response.getData();
    }

    /**
     * Search products by title or other fields
     * @param searchQuery The search query
     * @param first Number of results
     * @return Search results
     */
    public Map<String, Object> searchProducts(String searchQuery, int first) {
        logger.info("Searching products with query: {}", searchQuery);

        String query = String.format("""
            {
              products(first: %d, query: "%s") {
                edges {
                  node {
                    id
                    title
                    description
                    handle
                    status
                    vendor
                    productType
                    tags
                    variants(first: 5) {
                      edges {
                        node {
                          id
                          title
                          sku
                          price
                        }
                      }
                    }
                    images(first: 1) {
                      edges {
                        node {
                          url
                        }
                      }
                    }
                  }
                }
              }
            }
            """, Math.min(first, 250), searchQuery);

        GraphQLResponse response = graphQLClient.executeQuery(query);

        if (response.hasErrors()) {
            logger.error("Error searching products: {}", response.getErrors());
            throw new RuntimeException("Failed to search products: " +
                    response.getErrors().get(0).getMessage());
        }

        return response.getData();
    }
}
