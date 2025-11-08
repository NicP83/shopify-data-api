package com.shopify.api.controller;

import com.shopify.api.model.ApiResponse;
import com.shopify.api.model.inventory.*;
import com.shopify.api.repository.inventory.OrderRecommendationRepository;
import com.shopify.api.repository.inventory.SalesVelocityRepository;
import com.shopify.api.repository.inventory.StockAlertRepository;
import com.shopify.api.service.inventory.InventoryAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for Inventory Management Module
 * Provides endpoints for inventory analysis, recommendations, and alerts
 */
@RestController
@RequestMapping("/api/inventory-management")
public class InventoryAnalysisController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryAnalysisController.class);

    private final InventoryAnalysisService analysisService;
    private final SalesVelocityRepository velocityRepository;
    private final OrderRecommendationRepository recommendationRepository;
    private final StockAlertRepository alertRepository;

    public InventoryAnalysisController(
            InventoryAnalysisService analysisService,
            SalesVelocityRepository velocityRepository,
            OrderRecommendationRepository recommendationRepository,
            StockAlertRepository alertRepository) {
        this.analysisService = analysisService;
        this.velocityRepository = velocityRepository;
        this.recommendationRepository = recommendationRepository;
        this.alertRepository = alertRepository;
    }

    /**
     * GET /api/inventory-analysis/dashboard
     * Get dashboard overview with summary statistics
     */
    @GetMapping("/dashboard")
    public Mono<ResponseEntity<ApiResponse<InventoryDashboard>>> getDashboard() {
        logger.info("GET /api/inventory-analysis/dashboard");

        return analysisService.getDashboardData()
                .map(dashboard -> ResponseEntity.ok(ApiResponse.success(dashboard)))
                .onErrorResume(error -> {
                    logger.error("Error fetching dashboard: {}", error.getMessage(), error);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error("Failed to fetch dashboard", error.getMessage())));
                });
    }

    /**
     * GET /api/inventory-analysis/low-stock
     * Get products with low stock alerts
     *
     * @param level Alert level filter (CRITICAL, WARNING, INFO)
     * @param limit Maximum number of results
     */
    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<List<StockAlert>>> getLowStockProducts(
            @RequestParam(defaultValue = "WARNING") String level,
            @RequestParam(defaultValue = "50") int limit) {

        logger.info("GET /api/inventory-analysis/low-stock?level={}&limit={}", level, limit);

        try {
            AlertLevel alertLevel = AlertLevel.valueOf(level.toUpperCase());

            List<StockAlert> alerts = alertRepository
                    .findByResolvedFalseAndAlertLevelOrderByCreatedAtDesc(alertLevel)
                    .stream()
                    .limit(limit)
                    .toList();

            logger.info("Found {} {} alerts", alerts.size(), alertLevel);

            return ResponseEntity.ok(ApiResponse.success(alerts));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid alert level: {}", level);
            return ResponseEntity
                    .badRequest()
                    .body(ApiResponse.error("Invalid alert level", "Valid values: CRITICAL, WARNING, INFO"));
        } catch (Exception e) {
            logger.error("Error fetching low stock products: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch low stock products", e.getMessage()));
        }
    }

    /**
     * GET /api/inventory-analysis/recommendations
     * Get order recommendations
     *
     * @param status Recommendation status filter (PENDING, APPROVED, ORDERED, DISMISSED)
     * @param urgency Urgency level filter (CRITICAL, HIGH, MEDIUM, LOW)
     */
    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<List<OrderRecommendation>>> getRecommendations(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String urgency) {

        logger.info("GET /api/inventory-analysis/recommendations?status={}&urgency={}", status, urgency);

        try {
            List<OrderRecommendation> recommendations;

            if (status != null && urgency != null) {
                RecommendationStatus statusEnum = RecommendationStatus.valueOf(status.toUpperCase());
                UrgencyLevel urgencyEnum = UrgencyLevel.valueOf(urgency.toUpperCase());
                recommendations = recommendationRepository.findByStatusAndUrgency(statusEnum, urgencyEnum);
            } else if (status != null) {
                RecommendationStatus statusEnum = RecommendationStatus.valueOf(status.toUpperCase());
                recommendations = recommendationRepository.findByStatus(statusEnum);
            } else if (urgency != null) {
                UrgencyLevel urgencyEnum = UrgencyLevel.valueOf(urgency.toUpperCase());
                recommendations = recommendationRepository.findByUrgency(urgencyEnum);
            } else {
                // Default: pending recommendations
                recommendations = recommendationRepository.findByStatus(RecommendationStatus.PENDING);
            }

            logger.info("Found {} recommendations", recommendations.size());

            return ResponseEntity.ok(ApiResponse.success(recommendations));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid parameter value: {}", e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(ApiResponse.error("Invalid parameter", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error fetching recommendations: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch recommendations", e.getMessage()));
        }
    }

    /**
     * POST /api/inventory-analysis/analyze/{sku}
     * Analyze a specific product
     *
     * @param sku Product SKU
     * @param velocityDays Number of days for velocity calculation (default: 30)
     */
    @PostMapping("/analyze/{sku}")
    public Mono<ResponseEntity<ApiResponse<InventoryAnalysisResult>>> analyzeProduct(
            @PathVariable String sku,
            @RequestParam(defaultValue = "30") int velocityDays) {

        logger.info("POST /api/inventory-analysis/analyze/{} (velocityDays={})", sku, velocityDays);

        if (!analysisService.isEnabled()) {
            return Mono.just(ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Inventory analysis service is disabled", null)));
        }

        return analysisService.analyzeSingleProduct(sku)
                .map(result -> {
                    logger.info("Analysis completed for SKU: {}", sku);
                    return ResponseEntity.ok(ApiResponse.success(result));
                })
                .onErrorResume(error -> {
                    logger.error("Error analyzing SKU {}: {}", sku, error.getMessage(), error);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error("Analysis failed", error.getMessage())));
                });
    }

    /**
     * GET /api/inventory-analysis/velocity/{sku}
     * Get sales velocity for a product
     *
     * @param sku Product SKU
     */
    @GetMapping("/velocity/{sku}")
    public ResponseEntity<ApiResponse<SalesVelocity>> getVelocity(@PathVariable String sku) {
        logger.info("GET /api/inventory-analysis/velocity/{}", sku);

        try {
            return velocityRepository.findBySku(sku)
                    .map(velocity -> {
                        logger.info("Found velocity for SKU: {}", sku);
                        return ResponseEntity.ok(ApiResponse.success(velocity));
                    })
                    .orElseGet(() -> {
                        logger.warn("No velocity found for SKU: {}", sku);
                        return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error("Velocity not found", "No velocity data for SKU: " + sku));
                    });
        } catch (Exception e) {
            logger.error("Error fetching velocity for SKU {}: {}", sku, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch velocity", e.getMessage()));
        }
    }

    /**
     * PUT /api/inventory-analysis/recommendations/{id}/approve
     * Approve a recommendation
     *
     * @param id Recommendation ID
     */
    @PutMapping("/recommendations/{id}/approve")
    public ResponseEntity<ApiResponse<OrderRecommendation>> approveRecommendation(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {

        logger.info("PUT /api/inventory-analysis/recommendations/{}/approve", id);

        try {
            String approvedBy = body != null ? body.get("approvedBy") : "system";

            return recommendationRepository.findById(id)
                    .map(recommendation -> {
                        recommendation.approve(approvedBy);
                        OrderRecommendation saved = recommendationRepository.save(recommendation);

                        logger.info("Recommendation {} approved by {}", id, approvedBy);

                        return ResponseEntity.ok(ApiResponse.success(saved));
                    })
                    .orElseGet(() -> {
                        logger.warn("Recommendation not found: {}", id);
                        return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error("Recommendation not found", "ID: " + id));
                    });
        } catch (Exception e) {
            logger.error("Error approving recommendation {}: {}", id, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to approve recommendation", e.getMessage()));
        }
    }

    /**
     * DELETE /api/inventory-analysis/recommendations/{id}
     * Dismiss a recommendation
     *
     * @param id Recommendation ID
     */
    @DeleteMapping("/recommendations/{id}")
    public ResponseEntity<ApiResponse<String>> dismissRecommendation(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {

        logger.info("DELETE /api/inventory-analysis/recommendations/{}", id);

        try {
            String reason = body != null ? body.get("reason") : "User dismissed";
            String dismissedBy = body != null ? body.get("dismissedBy") : "system";

            return recommendationRepository.findById(id)
                    .map(recommendation -> {
                        recommendation.dismiss();
                        recommendationRepository.save(recommendation);

                        logger.info("Recommendation {} dismissed by {}: {}", id, dismissedBy, reason);

                        return ResponseEntity.ok(
                                ApiResponse.success("Recommendation dismissed successfully"));
                    })
                    .orElseGet(() -> {
                        logger.warn("Recommendation not found: {}", id);
                        return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error("Recommendation not found", "ID: " + id));
                    });
        } catch (Exception e) {
            logger.error("Error dismissing recommendation {}: {}", id, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to dismiss recommendation", e.getMessage()));
        }
    }

    /**
     * PUT /api/inventory-analysis/alerts/{id}/resolve
     * Resolve a stock alert
     *
     * @param id Alert ID
     */
    @PutMapping("/alerts/{id}/resolve")
    public ResponseEntity<ApiResponse<StockAlert>> resolveAlert(@PathVariable Long id) {
        logger.info("PUT /api/inventory-analysis/alerts/{}/resolve", id);

        try {
            return alertRepository.findById(id)
                    .map(alert -> {
                        alert.resolve();
                        StockAlert saved = alertRepository.save(alert);

                        logger.info("Alert {} resolved", id);

                        return ResponseEntity.ok(ApiResponse.success(saved));
                    })
                    .orElseGet(() -> {
                        logger.warn("Alert not found: {}", id);
                        return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error("Alert not found", "ID: " + id));
                    });
        } catch (Exception e) {
            logger.error("Error resolving alert {}: {}", id, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to resolve alert", e.getMessage()));
        }
    }

    /**
     * GET /api/inventory-management/health
     * Health check for the analysis module
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHealth() {
        logger.debug("GET /api/inventory-management/health");

        try {
            Map<String, Object> health = Map.of(
                    "status", "healthy",
                    "enabled", analysisService.isEnabled(),
                    "timestamp", LocalDateTime.now(),
                    "statistics", Map.of(
                            "velocities", velocityRepository.count(),
                            "recommendations", recommendationRepository.count(),
                            "alerts", alertRepository.count()
                    )
            );

            return ResponseEntity.ok(ApiResponse.success(health));
        } catch (Exception e) {
            logger.error("Error checking health: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Health check failed", e.getMessage()));
        }
    }

    /**
     * GET /api/inventory-management/by-brand/{brand}
     * Get inventory analysis filtered by brand
     */
    @GetMapping("/by-brand/{brand}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getByBrand(
            @PathVariable String brand,
            @RequestParam(defaultValue = "30") int days) {

        logger.info("GET /api/inventory-management/by-brand/{}?days={}", brand, days);

        try {
            // Get all velocities and filter by brand (simplified)
            List<SalesVelocity> allVelocities = velocityRepository.findAll();

            List<SalesVelocity> brandVelocities = allVelocities.stream()
                    .filter(v -> v.getSku() != null && matchesBrand(v.getSku(), brand))
                    .toList();

            long lowStockCount = brandVelocities.stream()
                    .filter(v -> {
                        List<StockAlert> alerts = alertRepository.findBySkuOrderByCreatedAtDesc(v.getSku());
                        return !alerts.isEmpty() && alerts.stream().anyMatch(a -> !a.getResolved());
                    })
                    .count();

            Map<String, Object> result = Map.of(
                    "brand", brand,
                    "days", days,
                    "totalProducts", brandVelocities.size(),
                    "lowStockCount", lowStockCount,
                    "products", brandVelocities
            );

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            logger.error("Error analyzing brand {}: {}", brand, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to analyze brand", e.getMessage()));
        }
    }

    /**
     * GET /api/inventory-management/by-category/{category}
     * Get inventory analysis filtered by category
     */
    @GetMapping("/by-category/{category}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "30") int days) {

        logger.info("GET /api/inventory-management/by-category/{}?days={}", category, days);

        try {
            List<SalesVelocity> allVelocities = velocityRepository.findAll();

            List<SalesVelocity> categoryVelocities = allVelocities.stream()
                    .filter(v -> v.getSku() != null && matchesCategory(v.getSku(), category))
                    .toList();

            long lowStockCount = categoryVelocities.stream()
                    .filter(v -> {
                        List<StockAlert> alerts = alertRepository.findBySkuOrderByCreatedAtDesc(v.getSku());
                        return !alerts.isEmpty() && alerts.stream().anyMatch(a -> !a.getResolved());
                    })
                    .count();

            Map<String, Object> result = Map.of(
                    "category", category,
                    "days", days,
                    "totalProducts", categoryVelocities.size(),
                    "lowStockCount", lowStockCount,
                    "products", categoryVelocities
            );

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            logger.error("Error analyzing category {}: {}", category, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to analyze category", e.getMessage()));
        }
    }

    /**
     * GET /api/inventory-management/by-supplier/{supplier}
     * Get order analysis filtered by supplier
     */
    @GetMapping("/by-supplier/{supplier}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBySupplier(
            @PathVariable String supplier) {

        logger.info("GET /api/inventory-management/by-supplier/{}", supplier);

        try {
            List<OrderRecommendation> supplierRecs = recommendationRepository
                    .findAll()
                    .stream()
                    .filter(r -> r.getSupplierName() != null &&
                                r.getSupplierName().equalsIgnoreCase(supplier))
                    .toList();

            Map<String, Object> result = Map.of(
                    "supplier", supplier,
                    "totalProducts", supplierRecs.size(),
                    "recommendations", supplierRecs
            );

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            logger.error("Error analyzing supplier {}: {}", supplier, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to analyze supplier", e.getMessage()));
        }
    }

    /**
     * GET /api/inventory-management/brands
     * Get list of all brands
     */
    @GetMapping("/brands")
    public ResponseEntity<ApiResponse<List<String>>> getBrands() {
        logger.info("GET /api/inventory-management/brands");

        try {
            // In production, this would come from ERP metadata
            List<String> brands = List.of(
                    "Tamiya", "Bandai", "Gundam", "Vallejo", "Citadel",
                    "Testors", "Mr. Hobby", "Games Workshop", "Revell", "Italeri"
            );

            return ResponseEntity.ok(ApiResponse.success(brands));

        } catch (Exception e) {
            logger.error("Error getting brands: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get brands", e.getMessage()));
        }
    }

    /**
     * GET /api/inventory-management/categories
     * Get list of all categories
     */
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<String>>> getCategories() {
        logger.info("GET /api/inventory-management/categories");

        try {
            // In production, this would come from ERP metadata
            List<String> categories = List.of(
                    "Model Kits", "Acrylic Paints", "Enamel Paints", "Tools",
                    "Brushes", "Accessories", "Decals", "Weathering Supplies",
                    "Airbrushes", "Glues & Adhesives"
            );

            return ResponseEntity.ok(ApiResponse.success(categories));

        } catch (Exception e) {
            logger.error("Error getting categories: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get categories", e.getMessage()));
        }
    }

    // ===== HELPER METHODS =====

    private boolean matchesBrand(String sku, String brand) {
        if (sku == null || brand == null) return false;
        String skuUpper = sku.toUpperCase();
        String brandUpper = brand.toUpperCase();
        return skuUpper.contains(brandUpper) ||
               skuUpper.startsWith(brandUpper.substring(0, Math.min(3, brandUpper.length())));
    }

    private boolean matchesCategory(String sku, String category) {
        if (sku == null || category == null) return false;
        String categoryUpper = category.toUpperCase();
        if (categoryUpper.contains("PAINT")) {
            return sku.toUpperCase().contains("PAINT") || sku.toUpperCase().contains("CLR");
        } else if (categoryUpper.contains("KIT") || categoryUpper.contains("MODEL")) {
            return sku.toUpperCase().contains("KIT") || sku.toUpperCase().contains("MDL");
        }
        return false;
    }
}
