package com.shopify.api.controller;

import com.shopify.api.model.ApiResponse;
import com.shopify.api.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Inventory operations
 * Provides endpoints to access Shopify inventory data
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryController.class);

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * GET /api/inventory
     * Fetch inventory levels for all products
     *
     * @param first Number of products to fetch (default: 50, max: 250)
     * @return Inventory data for products
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInventory(
            @RequestParam(defaultValue = "50") int first) {

        logger.info("GET /api/inventory - first: {}", first);

        try {
            Map<String, Object> inventory = inventoryService.getInventory(first);
            return ResponseEntity.ok(ApiResponse.success(inventory));
        } catch (Exception e) {
            logger.error("Error fetching inventory", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch inventory", e.getMessage()));
        }
    }

    /**
     * GET /api/inventory/product/{id}
     * Fetch inventory for a specific product
     *
     * @param id Shopify product ID
     * @return Product inventory details
     */
    @GetMapping("/product/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInventoryByProductId(
            @PathVariable String id) {

        logger.info("GET /api/inventory/product/{}", id);

        try {
            String productId = id.startsWith("gid://") ? id : "gid://shopify/Product/" + id;
            Map<String, Object> inventory = inventoryService.getInventoryByProductId(productId);
            return ResponseEntity.ok(ApiResponse.success(inventory));
        } catch (Exception e) {
            logger.error("Error fetching inventory for product {}", id, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch product inventory", e.getMessage()));
        }
    }

    /**
     * GET /api/inventory/locations
     * Fetch all inventory locations
     *
     * @return List of inventory locations
     */
    @GetMapping("/locations")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLocations() {

        logger.info("GET /api/inventory/locations");

        try {
            Map<String, Object> locations = inventoryService.getLocations();
            return ResponseEntity.ok(ApiResponse.success(locations));
        } catch (Exception e) {
            logger.error("Error fetching locations", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch locations", e.getMessage()));
        }
    }
}
