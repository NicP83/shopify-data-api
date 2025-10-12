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
        return searchProducts(searchQuery, first, includeArchived, null);
    }

    /**
     * Search products with optional product type filtering
     * @param searchQuery The search query
     * @param first Number of results
     * @param includeArchived Whether to include archived products
     * @param productType Optional product type filter (e.g., "PLASTIC KITS")
     * @return Search results
     */
    public Map<String, Object> searchProducts(String searchQuery, int first, boolean includeArchived, String productType) {
        logger.info("Searching products with query: '{}', includeArchived: {}, productType: {}",
                    searchQuery, includeArchived, productType);

        // Use fallback strategy for better results
        return searchWithFallback(searchQuery, first, includeArchived, productType);
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
        return searchProductsReactive(searchQuery, first, includeArchived, null);
    }

    /**
     * Search products reactively with optional product type filtering
     * @param searchQuery The search query
     * @param first Number of results
     * @param includeArchived Whether to include archived products
     * @param productType Optional product type filter (e.g., "PLASTIC KITS")
     * @return Mono of search results
     */
    public Mono<Map<String, Object>> searchProductsReactive(String searchQuery, int first, boolean includeArchived, String productType) {
        logger.info("Searching products (reactive) with query: '{}', includeArchived: {}, productType: {}",
                    searchQuery, includeArchived, productType);

        // Use reactive fallback strategy
        return searchWithFallbackReactive(searchQuery, first, includeArchived, productType);
    }

    /**
     * Try multi-level search with fallback strategy (reactive version)
     * @param userQuery The user's search query
     * @param first Number of results
     * @param includeArchived Whether to include archived products
     * @param productType Optional product type filter
     * @return Mono of search results (tries multiple strategies)
     */
    private Mono<Map<String, Object>> searchWithFallbackReactive(String userQuery, int first, boolean includeArchived, String productType) {
        // Strategy 1: Search with product type filter (if specified)
        if (productType != null && !productType.trim().isEmpty()) {
            logger.info("Trying search with product type filter: {}", productType);
            return executeSearchReactive(userQuery, first, includeArchived, productType)
                    .flatMap(results -> {
                        if (hasResults(results)) {
                            logger.info("Found {} results with product type filter", getResultCount(results));
                            return Mono.just(results);
                        }
                        // If no results, try without product type filter
                        logger.info("No results with product type filter, trying without");
                        return searchWithoutProductTypeReactive(userQuery, first, includeArchived);
                    });
        }

        // Strategy 2: Search without product type filter
        return searchWithoutProductTypeReactive(userQuery, first, includeArchived);
    }

    /**
     * Search without product type filter (reactive)
     */
    private Mono<Map<String, Object>> searchWithoutProductTypeReactive(String userQuery, int first, boolean includeArchived) {
        logger.info("Trying search without product type filter");
        return executeSearchReactive(userQuery, first, includeArchived, null)
                .flatMap(results -> {
                    if (hasResults(results)) {
                        logger.info("Found {} results without product type filter", getResultCount(results));
                        return Mono.just(results);
                    }
                    // Strategy 3: Very broad search (search in description too)
                    logger.info("Trying broad search including description");
                    return executeBroadSearchReactive(userQuery, first, includeArchived)
                            .map(broadResults -> {
                                if (hasResults(broadResults)) {
                                    logger.info("Found {} results with broad search", getResultCount(broadResults));
                                } else {
                                    logger.warn("No results found for query: {}", userQuery);
                                }
                                return broadResults;
                            });
                });
    }

    /**
     * Execute search reactively with specified parameters
     */
    private Mono<Map<String, Object>> executeSearchReactive(String userQuery, int first, boolean includeArchived, String productType) {
        String shopifyQuery = buildAdvancedSearchQuery(userQuery, includeArchived, productType);
        String query = buildGraphQLQuery(shopifyQuery, first);

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
     * Execute broad search reactively including description field
     */
    private Mono<Map<String, Object>> executeBroadSearchReactive(String userQuery, int first, boolean includeArchived) {
        String escapedQuery = userQuery.replace("\"", "\\\"");
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("(");
        queryBuilder.append("title:*").append(escapedQuery).append("*");
        queryBuilder.append(" OR body:*").append(escapedQuery).append("*");
        queryBuilder.append(" OR tag:*").append(escapedQuery).append("*");
        queryBuilder.append(" OR vendor:*").append(escapedQuery).append("*");
        queryBuilder.append(")");

        if (!includeArchived) {
            queryBuilder.append(" AND status:active");
        }

        String query = buildGraphQLQuery(queryBuilder.toString(), first);

        return graphQLClient.executeQueryReactive(query)
                .map(response -> {
                    if (response.hasErrors()) {
                        logger.error("Error in broad search: {}", response.getErrors());
                        throw new RuntimeException("Failed to search products: " +
                                response.getErrors().get(0).getMessage());
                    }
                    return response.getData();
                });
    }

    /**
     * Build advanced Shopify search query with relevance-based ranking and fallback strategy
     * @param userQuery The user's search query
     * @param includeArchived Whether to include archived products
     * @return Formatted Shopify query string
     */
    private String buildAdvancedSearchQuery(String userQuery, boolean includeArchived) {
        return buildAdvancedSearchQuery(userQuery, includeArchived, null);
    }

    /**
     * Build advanced Shopify search query with relevance-based ranking, product type filtering, and fallback strategy
     * @param userQuery The user's search query
     * @param includeArchived Whether to include archived products
     * @param productType Optional product type filter (e.g., "PLASTIC KITS", "PAINTS")
     * @return Formatted Shopify query string
     */
    private String buildAdvancedSearchQuery(String userQuery, boolean includeArchived, String productType) {
        // Escape special characters for Shopify query syntax
        String escapedQuery = userQuery.replace("\"", "\\\"");

        // Build relevance-based multi-field search query
        // Priority: 1) Exact title match, 2) Title contains, 3) Tags, 4) Description/Vendor
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("(");

        // High relevance: Title contains the search term (most common use case)
        queryBuilder.append("title:*").append(escapedQuery).append("*");

        // Medium relevance: Tags (specific categorization)
        queryBuilder.append(" OR tag:*").append(escapedQuery).append("*");

        // Lower relevance: Vendor (might be too broad)
        queryBuilder.append(" OR vendor:*").append(escapedQuery).append("*");

        queryBuilder.append(")");

        // Add product type filter if specified
        if (productType != null && !productType.trim().isEmpty()) {
            queryBuilder.append(" AND product_type:\"").append(productType.replace("\"", "\\\"")).append("\"");
        }

        // Add status filter if not including archived
        if (!includeArchived) {
            queryBuilder.append(" AND status:active");
        }

        logger.debug("Built search query: {}", queryBuilder.toString());
        return queryBuilder.toString();
    }

    /**
     * Try multi-level search with fallback strategy for better results
     * @param userQuery The user's search query
     * @param first Number of results
     * @param includeArchived Whether to include archived products
     * @param productType Optional product type filter
     * @return Search results (tries multiple strategies)
     */
    private Map<String, Object> searchWithFallback(String userQuery, int first, boolean includeArchived, String productType) {
        // Strategy 1: Search with product type filter (if specified)
        if (productType != null && !productType.trim().isEmpty()) {
            logger.info("Trying search with product type filter: {}", productType);
            Map<String, Object> results = executeSearch(userQuery, first, includeArchived, productType);
            if (hasResults(results)) {
                logger.info("Found {} results with product type filter", getResultCount(results));
                return results;
            }
        }

        // Strategy 2: Search without product type filter (broader)
        logger.info("Trying search without product type filter");
        Map<String, Object> results = executeSearch(userQuery, first, includeArchived, null);
        if (hasResults(results)) {
            logger.info("Found {} results without product type filter", getResultCount(results));
            return results;
        }

        // Strategy 3: Very broad search (search in description too)
        logger.info("Trying broad search including description");
        results = executeBroadSearch(userQuery, first, includeArchived);
        if (hasResults(results)) {
            logger.info("Found {} results with broad search", getResultCount(results));
            return results;
        }

        // No results found with any strategy
        logger.warn("No results found for query: {}", userQuery);
        return results;
    }

    /**
     * Execute search with specified parameters
     */
    private Map<String, Object> executeSearch(String userQuery, int first, boolean includeArchived, String productType) {
        String shopifyQuery = buildAdvancedSearchQuery(userQuery, includeArchived, productType);
        String query = buildGraphQLQuery(shopifyQuery, first);
        GraphQLResponse response = graphQLClient.executeQuery(query);

        if (response.hasErrors()) {
            logger.error("Error searching products: {}", response.getErrors());
            throw new RuntimeException("Failed to search products: " +
                    response.getErrors().get(0).getMessage());
        }

        return response.getData();
    }

    /**
     * Execute broad search including description field
     */
    private Map<String, Object> executeBroadSearch(String userQuery, int first, boolean includeArchived) {
        String escapedQuery = userQuery.replace("\"", "\\\"");
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("(");
        queryBuilder.append("title:*").append(escapedQuery).append("*");
        queryBuilder.append(" OR body:*").append(escapedQuery).append("*");
        queryBuilder.append(" OR tag:*").append(escapedQuery).append("*");
        queryBuilder.append(" OR vendor:*").append(escapedQuery).append("*");
        queryBuilder.append(")");

        if (!includeArchived) {
            queryBuilder.append(" AND status:active");
        }

        String query = buildGraphQLQuery(queryBuilder.toString(), first);
        GraphQLResponse response = graphQLClient.executeQuery(query);

        if (response.hasErrors()) {
            logger.error("Error in broad search: {}", response.getErrors());
            throw new RuntimeException("Failed to search products: " +
                    response.getErrors().get(0).getMessage());
        }

        return response.getData();
    }

    /**
     * Build GraphQL query string
     */
    private String buildGraphQLQuery(String shopifyQuery, int first) {
        return String.format("""
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
    }

    /**
     * Check if search results contain any products
     */
    private boolean hasResults(Map<String, Object> data) {
        if (data == null || !data.containsKey("products")) {
            return false;
        }
        Map<String, Object> products = (Map<String, Object>) data.get("products");
        if (products == null || !products.containsKey("edges")) {
            return false;
        }
        List<Map<String, Object>> edges = (List<Map<String, Object>>) products.get("edges");
        return edges != null && !edges.isEmpty();
    }

    /**
     * Get count of results
     */
    private int getResultCount(Map<String, Object> data) {
        if (!hasResults(data)) {
            return 0;
        }
        Map<String, Object> products = (Map<String, Object>) data.get("products");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) products.get("edges");
        return edges.size();
    }
}
