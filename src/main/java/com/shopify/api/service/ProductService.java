package com.shopify.api.service;

import com.shopify.api.client.ShopifyGraphQLClient;
import com.shopify.api.model.GraphQLResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

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
                    onlineStoreUrl
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
                onlineStoreUrl
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
        return searchProducts(searchQuery, first, false);
    }

    /**
     * Search products by title, body, tags, vendor with optional archived filter
     * @param searchQuery The search query
     * @param first Number of results
     * @param includeArchived Whether to include archived products
     * @return Search results
     */
    public Map<String, Object> searchProducts(String searchQuery, int first, boolean includeArchived) {
        logger.info("Searching products with query: '{}', includeArchived: {}", searchQuery, includeArchived);

        // Build advanced Shopify query syntax
        String shopifyQuery = buildAdvancedSearchQuery(searchQuery, includeArchived);
        logger.debug("Built Shopify query: {}", shopifyQuery);

        String query = String.format("""
            {
              products(first: %d, query: "%s") {
                edges {
                  node {
                    id
                    title
                    description
                    handle
                    onlineStoreUrl
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
            """, Math.min(first, 250), shopifyQuery);

        GraphQLResponse response = graphQLClient.executeQuery(query);

        if (response.hasErrors()) {
            logger.error("Error searching products: {}", response.getErrors());
            throw new RuntimeException("Failed to search products: " +
                    response.getErrors().get(0).getMessage());
        }

        return response.getData();
    }

    /**
     * Search products reactively (non-blocking) - for use in reactive contexts
     * @param searchQuery The search query
     * @param first Number of results
     * @return Mono of search results
     */
    public Mono<Map<String, Object>> searchProductsReactive(String searchQuery, int first) {
        return searchProductsReactive(searchQuery, first, false);
    }

    /**
     * Search products reactively (non-blocking) - for use in reactive contexts
     * @param searchQuery The search query
     * @param first Number of results
     * @param includeArchived Whether to include archived products
     * @return Mono of search results
     */
    public Mono<Map<String, Object>> searchProductsReactive(String searchQuery, int first, boolean includeArchived) {
        logger.info("Searching products (reactive) with query: '{}', includeArchived: {}", searchQuery, includeArchived);

        // Build advanced Shopify query syntax
        String shopifyQuery = buildAdvancedSearchQuery(searchQuery, includeArchived);
        logger.debug("Built Shopify query: {}", shopifyQuery);

        String query = String.format("""
            {
              products(first: %d, query: "%s") {
                edges {
                  node {
                    id
                    title
                    description
                    handle
                    onlineStoreUrl
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
            """, Math.min(first, 250), shopifyQuery);

        return graphQLClient.executeQueryReactive(query)
                .map(response -> {
                    if (response.hasErrors()) {
                        logger.error("Error searching products: {}", response.getErrors());
                        throw new RuntimeException("Failed to search products: " +
                                response.getErrors().get(0).getMessage());
                    }
                    return response.getData();
                });
    }

    /**
     * Build advanced Shopify search query with multi-field search and status filtering
     * @param userQuery The user's search query
     * @param includeArchived Whether to include archived products
     * @return Formatted Shopify query string
     */
    private String buildAdvancedSearchQuery(String userQuery, boolean includeArchived) {
        // Escape special characters for Shopify query syntax
        String escapedQuery = userQuery.replace("\"", "\\\"");

        // Build multi-field search query
        // Searches: title, body (description), tags, and vendor
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("(");
        queryBuilder.append("title:*").append(escapedQuery).append("*");
        queryBuilder.append(" OR body:*").append(escapedQuery).append("*");
        queryBuilder.append(" OR tag:").append(escapedQuery);
        queryBuilder.append(" OR vendor:*").append(escapedQuery).append("*");
        queryBuilder.append(")");

        // Add status filter if not including archived
        if (!includeArchived) {
            queryBuilder.append(" AND status:active");
        }

        return queryBuilder.toString();
    }
}
