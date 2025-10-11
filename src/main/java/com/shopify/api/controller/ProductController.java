package com.shopify.api.controller;

import com.shopify.api.model.ApiResponse;
import com.shopify.api.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Product operations
 * Provides endpoints to access Shopify product data
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * GET /api/products
     * Fetch all products with pagination
     *
     * @param first Number of products to fetch (default: 50, max: 250)
     * @return List of products
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProducts(
            @RequestParam(defaultValue = "50") int first) {

        logger.info("GET /api/products - first: {}", first);

        try {
            Map<String, Object> products = productService.getProducts(first);
            return ResponseEntity.ok(ApiResponse.success(products));
        } catch (Exception e) {
            logger.error("Error fetching products", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch products", e.getMessage()));
        }
    }

    /**
     * GET /api/products/{id}
     * Fetch a single product by ID
     *
     * @param id Shopify product ID (e.g., gid://shopify/Product/123456)
     * @return Product details
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProductById(
            @PathVariable String id) {

        logger.info("GET /api/products/{}", id);

        try {
            // Ensure ID has proper Shopify format
            String productId = id.startsWith("gid://") ? id : "gid://shopify/Product/" + id;

            Map<String, Object> product = productService.getProductById(productId);
            return ResponseEntity.ok(ApiResponse.success(product));
        } catch (Exception e) {
            logger.error("Error fetching product {}", id, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch product", e.getMessage()));
        }
    }

    /**
     * GET /api/products/search
     * Search products by query with multi-field search (title, body, tags, vendor)
     *
     * @param q Search query
     * @param first Number of results (default: 20)
     * @param includeArchived Whether to include archived products (default: false)
     * @return Search results
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchProducts(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int first,
            @RequestParam(defaultValue = "false") boolean includeArchived) {

        logger.info("GET /api/products/search - query: {}, first: {}, includeArchived: {}", q, first, includeArchived);

        try {
            Map<String, Object> results = productService.searchProducts(q, first, includeArchived);
            return ResponseEntity.ok(ApiResponse.success(results));
        } catch (Exception e) {
            logger.error("Error searching products", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to search products", e.getMessage()));
        }
    }
}
