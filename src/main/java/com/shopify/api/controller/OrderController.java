package com.shopify.api.controller;

import com.shopify.api.model.ApiResponse;
import com.shopify.api.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Order operations
 * Provides endpoints to access Shopify order data
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * GET /api/orders
     * Fetch all orders with pagination
     *
     * @param first Number of orders to fetch (default: 50, max: 250)
     * @return List of orders
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrders(
            @RequestParam(defaultValue = "50") int first) {

        logger.info("GET /api/orders - first: {}", first);

        try {
            Map<String, Object> orders = orderService.getOrders(first);
            return ResponseEntity.ok(ApiResponse.success(orders));
        } catch (Exception e) {
            logger.error("Error fetching orders", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch orders", e.getMessage()));
        }
    }

    /**
     * GET /api/orders/{id}
     * Fetch a single order by ID
     *
     * @param id Shopify order ID
     * @return Order details
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrderById(
            @PathVariable String id) {

        logger.info("GET /api/orders/{}", id);

        try {
            String orderId = id.startsWith("gid://") ? id : "gid://shopify/Order/" + id;
            Map<String, Object> order = orderService.getOrderById(orderId);
            return ResponseEntity.ok(ApiResponse.success(order));
        } catch (Exception e) {
            logger.error("Error fetching order {}", id, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch order", e.getMessage()));
        }
    }

    /**
     * GET /api/orders/search
     * Search orders by query
     *
     * @param q Search query (e.g., email:customer@example.com, status:open)
     * @param first Number of results (default: 20)
     * @return Search results
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchOrders(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int first) {

        logger.info("GET /api/orders/search - query: {}, first: {}", q, first);

        try {
            Map<String, Object> results = orderService.searchOrders(q, first);
            return ResponseEntity.ok(ApiResponse.success(results));
        } catch (Exception e) {
            logger.error("Error searching orders", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to search orders", e.getMessage()));
        }
    }
}
