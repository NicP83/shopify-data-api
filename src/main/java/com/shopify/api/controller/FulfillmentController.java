package com.shopify.api.controller;

import com.shopify.api.model.ApiResponse;
import com.shopify.api.model.UnfulfilledOrder;
import com.shopify.api.service.FulfillmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Order Fulfillment operations
 * Provides endpoints to check orders pending fulfillment in CRS
 */
@RestController
@RequestMapping("/api/fulfillment")
public class FulfillmentController {

    private static final Logger logger = LoggerFactory.getLogger(FulfillmentController.class);

    private final FulfillmentService fulfillmentService;

    public FulfillmentController(FulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    /**
     * GET /api/fulfillment/pending
     * Fetch all orders that are unfulfilled in Shopify and not yet in CRS
     *
     * @param includeDiscounts Whether to fetch discount information (default: false for faster load)
     * @param includeSalePrices Whether to fetch CRS sale prices (default: false for faster load)
     * @return List of unfulfilled orders
     */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<UnfulfilledOrder>>> getPendingOrders(
            @RequestParam(defaultValue = "false") boolean includeDiscounts,
            @RequestParam(defaultValue = "false") boolean includeSalePrices) {

        logger.info("GET /api/fulfillment/pending - Fetching pending fulfillment orders (discounts={}, salePrices={})",
                    includeDiscounts, includeSalePrices);

        try {
            List<UnfulfilledOrder> orders = fulfillmentService.getUnfulfilledOrders(includeDiscounts, includeSalePrices);
            logger.info("Found {} orders pending fulfillment", orders.size());
            return ResponseEntity.ok(ApiResponse.success(orders));
        } catch (Exception e) {
            logger.error("Error fetching pending orders", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch pending orders", e.getMessage()));
        }
    }

    /**
     * GET /api/fulfillment/{orderId}
     * Get detailed information about a specific order
     *
     * @param orderId The Shopify order ID
     * @return Order details
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<UnfulfilledOrder>> getOrderDetails(
            @PathVariable String orderId) {

        logger.info("GET /api/fulfillment/{} - Fetching order details", orderId);

        try {
            // Fetch all orders with full data (discounts and sale prices) for single order lookup
            List<UnfulfilledOrder> orders = fulfillmentService.getUnfulfilledOrders(true, true);
            UnfulfilledOrder order = orders.stream()
                    .filter(o -> o.getOrderId().equals(orderId) || o.getOrderName().equals(orderId))
                    .findFirst()
                    .orElse(null);

            if (order == null) {
                logger.warn("Order {} not found", orderId);
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(ApiResponse.success(order));
        } catch (Exception e) {
            logger.error("Error fetching order {}", orderId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch order details", e.getMessage()));
        }
    }
}
