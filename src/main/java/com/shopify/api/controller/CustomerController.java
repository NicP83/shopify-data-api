package com.shopify.api.controller;

import com.shopify.api.model.ApiResponse;
import com.shopify.api.service.CustomerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Customer operations
 * Provides endpoints to access Shopify customer data
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private static final Logger logger = LoggerFactory.getLogger(CustomerController.class);

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /**
     * GET /api/customers
     * Fetch all customers with pagination
     *
     * @param first Number of customers to fetch (default: 50, max: 250)
     * @return List of customers
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCustomers(
            @RequestParam(defaultValue = "50") int first) {

        logger.info("GET /api/customers - first: {}", first);

        try {
            Map<String, Object> customers = customerService.getCustomers(first);
            return ResponseEntity.ok(ApiResponse.success(customers));
        } catch (Exception e) {
            logger.error("Error fetching customers", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch customers", e.getMessage()));
        }
    }

    /**
     * GET /api/customers/{id}
     * Fetch a single customer by ID with full details including order history
     *
     * @param id Shopify customer ID
     * @return Customer details
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCustomerById(
            @PathVariable String id) {

        logger.info("GET /api/customers/{}", id);

        try {
            String customerId = id.startsWith("gid://") ? id : "gid://shopify/Customer/" + id;
            Map<String, Object> customer = customerService.getCustomerById(customerId);
            return ResponseEntity.ok(ApiResponse.success(customer));
        } catch (Exception e) {
            logger.error("Error fetching customer {}", id, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch customer", e.getMessage()));
        }
    }

    /**
     * GET /api/customers/search
     * Search customers by query
     *
     * @param q Search query (e.g., email:customer@example.com, phone:555-1234)
     * @param first Number of results (default: 20)
     * @return Search results
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchCustomers(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int first) {

        logger.info("GET /api/customers/search - query: {}, first: {}", q, first);

        try {
            Map<String, Object> results = customerService.searchCustomers(q, first);
            return ResponseEntity.ok(ApiResponse.success(results));
        } catch (Exception e) {
            logger.error("Error searching customers", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to search customers", e.getMessage()));
        }
    }
}
