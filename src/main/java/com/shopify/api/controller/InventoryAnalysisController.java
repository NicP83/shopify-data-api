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
     * POST /api/inventory-management/analyze-batch
     * Analyze multiple products in batch
     *
     * @param request Map containing "skus" array
     */
    @PostMapping("/analyze-batch")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> analyzeBatch(
            @RequestBody Map<String, Object> request) {

        @SuppressWarnings("unchecked")
        List<String> skus = (List<String>) request.get("skus");

        if (skus == null || skus.isEmpty()) {
            return Mono.just(ResponseEntity
                    .badRequest()
                    .body(ApiResponse.error("No SKUs provided", "Request must contain 'skus' array")));
        }

        logger.info("POST /api/inventory-management/analyze-batch - Processing {} SKUs", skus.size());

        if (!analysisService.isEnabled()) {
            return Mono.just(ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Inventory analysis service is disabled", null)));
        }

        // Process each SKU sequentially (to avoid overwhelming the ERP/MCP)
        return reactor.core.publisher.Flux.fromIterable(skus)
                .flatMap(sku -> analysisService.analyzeSingleProduct(sku)
                        .map(result -> Map.of("sku", sku, "status", "success", "result", result))
                        .onErrorResume(error -> {
                            logger.error("Error analyzing SKU {} in batch: {}", sku, error.getMessage());
                            return Mono.just(Map.of(
                                    "sku", sku,
                                    "status", "error",
                                    "error", error.getMessage()
                            ));
                        }), 3) // Process 3 at a time
                .collectList()
                .map(results -> {
                    long successCount = results.stream()
                            .filter(r -> "success".equals(r.get("status")))
                            .count();

                    Map<String, Object> summary = Map.of(
                            "totalRequested", skus.size(),
                            "successCount", successCount,
                            "errorCount", results.size() - successCount,
                            "results", results
                    );

                    logger.info("Batch analysis completed: {}/{} successful", successCount, skus.size());

                    return ResponseEntity.ok(ApiResponse.success(summary));
                });
    }

    /**
     * POST /api/inventory-management/analyze-all
     * Analyze all tracked products
     * This fetches all existing SKUs from the velocity table and re-analyzes them
     *
     * @param limit Maximum number of products to analyze (default: 100)
     */
    @PostMapping("/analyze-all")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> analyzeAll(
            @RequestParam(defaultValue = "100") int limit) {

        logger.info("POST /api/inventory-management/analyze-all - Limit: {}", limit);

        if (!analysisService.isEnabled()) {
            return Mono.just(ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Inventory analysis service is disabled", null)));
        }

        // Get all SKUs from existing velocity records
        List<String> skus = velocityRepository.findAll()
                .stream()
                .map(SalesVelocity::getSku)
                .distinct()
                .limit(limit)
                .toList();

        if (skus.isEmpty()) {
            Map<String, Object> result = Map.of(
                    "message", "No products found to analyze",
                    "hint", "Use POST /api/inventory-management/analyze/{sku} to analyze individual products first"
            );
            return Mono.just(ResponseEntity.ok(ApiResponse.success(result)));
        }

        logger.info("Found {} SKUs to analyze", skus.size());

        // Process in batches
        return reactor.core.publisher.Flux.fromIterable(skus)
                .flatMap(sku -> analysisService.analyzeSingleProduct(sku)
                        .map(result -> Map.of("sku", sku, "status", "success"))
                        .onErrorResume(error -> {
                            logger.error("Error analyzing SKU {} in full analysis: {}", sku, error.getMessage());
                            return Mono.just(Map.of(
                                    "sku", sku,
                                    "status", "error",
                                    "error", error.getMessage()
                            ));
                        }), 3) // Process 3 at a time
                .collectList()
                .map(results -> {
                    long successCount = results.stream()
                            .filter(r -> "success".equals(r.get("status")))
                            .count();

                    Map<String, Object> summary = Map.of(
                            "totalAnalyzed", skus.size(),
                            "successCount", successCount,
                            "errorCount", results.size() - successCount,
                            "message", String.format("Analyzed %d products: %d successful, %d errors",
                                    skus.size(), successCount, results.size() - successCount)
                    );

                    logger.info("Full analysis completed: {}/{} successful", successCount, skus.size());

                    return ResponseEntity.ok(ApiResponse.success(summary));
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
     * POST /api/inventory-management/seed-test-data
     * Seed database with test data for development/testing
     * WARNING: This is for testing only!
     */
    @PostMapping("/seed-test-data")
    public ResponseEntity<ApiResponse<Map<String, Object>>> seedTestData() {
        logger.info("POST /api/inventory-management/seed-test-data");

        try {
            // Create sample velocity data
            java.util.List<SalesVelocity> velocities = createSampleVelocities();
            velocities = velocityRepository.saveAll(velocities);

            // Create sample alerts
            java.util.List<StockAlert> alerts = createSampleAlerts();
            alerts = alertRepository.saveAll(alerts);

            // Create sample recommendations
            java.util.List<OrderRecommendation> recommendations = createSampleRecommendations();
            recommendations = recommendationRepository.saveAll(recommendations);

            Map<String, Object> result = Map.of(
                    "message", "Test data seeded successfully",
                    "velocitiesCreated", velocities.size(),
                    "alertsCreated", alerts.size(),
                    "recommendationsCreated", recommendations.size()
            );

            logger.info("Test data seeded: {} velocities, {} alerts, {} recommendations",
                    velocities.size(), alerts.size(), recommendations.size());

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            logger.error("Error seeding test data: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to seed test data", e.getMessage()));
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

    // ===== TEST DATA GENERATION =====

    private java.util.List<SalesVelocity> createSampleVelocities() {
        return java.util.List.of(
                SalesVelocity.builder()
                        .sku("TAM-31114")
                        .productId("TAM-31114")
                        .dailyAverage(new java.math.BigDecimal("2.5"))
                        .weeklyAverage(new java.math.BigDecimal("17.5"))
                        .monthlyAverage(new java.math.BigDecimal("75.0"))
                        .trend(TrendDirection.INCREASING)
                        .trendPercentage(new java.math.BigDecimal("15.5"))
                        .dataSampleSize(120)
                        .calculationPeriodDays(30)
                        .lastCalculated(LocalDateTime.now())
                        .build(),
                SalesVelocity.builder()
                        .sku("GUN-RG-001")
                        .productId("GUN-RG-001")
                        .dailyAverage(new java.math.BigDecimal("4.2"))
                        .weeklyAverage(new java.math.BigDecimal("29.4"))
                        .monthlyAverage(new java.math.BigDecimal("126.0"))
                        .trend(TrendDirection.STABLE)
                        .trendPercentage(new java.math.BigDecimal("2.1"))
                        .dataSampleSize(85)
                        .calculationPeriodDays(30)
                        .lastCalculated(LocalDateTime.now())
                        .build(),
                SalesVelocity.builder()
                        .sku("VAL-70101")
                        .productId("VAL-70101")
                        .dailyAverage(new java.math.BigDecimal("1.8"))
                        .weeklyAverage(new java.math.BigDecimal("12.6"))
                        .monthlyAverage(new java.math.BigDecimal("54.0"))
                        .trend(TrendDirection.DECREASING)
                        .trendPercentage(new java.math.BigDecimal("-8.3"))
                        .dataSampleSize(95)
                        .calculationPeriodDays(30)
                        .lastCalculated(LocalDateTime.now())
                        .build()
        );
    }

    private java.util.List<StockAlert> createSampleAlerts() {
        return java.util.List.of(
                StockAlert.builder()
                        .sku("TAM-31114")
                        .productTitle("Tamiya 1/48 P-51D Mustang")
                        .currentStock(12)
                        .reorderPoint(25)
                        .alertLevel(AlertLevel.WARNING)
                        .daysUntilStockout(5)
                        .estimatedStockoutDate(java.time.LocalDate.now().plusDays(5))
                        .message("Stock running low - only 5 days remaining")
                        .resolved(false)
                        .build(),
                StockAlert.builder()
                        .sku("GUN-RG-001")
                        .productTitle("Gundam RG RX-78-2 Ver 2.0")
                        .currentStock(3)
                        .reorderPoint(15)
                        .alertLevel(AlertLevel.CRITICAL)
                        .daysUntilStockout(1)
                        .estimatedStockoutDate(java.time.LocalDate.now().plusDays(1))
                        .message("CRITICAL: Stock out in 1 day!")
                        .resolved(false)
                        .build()
        );
    }

    private java.util.List<OrderRecommendation> createSampleRecommendations() {
        return java.util.List.of(
                OrderRecommendation.builder()
                        .sku("TAM-31114")
                        .productTitle("Tamiya 1/48 P-51D Mustang")
                        .currentStock(12)
                        .erpStock(12)
                        .shopifyStock(0)
                        .recommendedQuantity(50)
                        .reorderPoint(25)
                        .safetyStockLevel(15)
                        .leadTimeDays(14)
                        .supplierName("Tamiya USA")
                        .costPerUnit(new java.math.BigDecimal("45.99"))
                        .totalCost(new java.math.BigDecimal("2299.50"))
                        .urgency(UrgencyLevel.HIGH)
                        .confidenceScore(new java.math.BigDecimal("0.92"))
                        .aiReasoning("Strong sales trend with upcoming holiday season. Current stock will deplete in 5 days.")
                        .estimatedStockoutDate(java.time.LocalDate.now().plusDays(5))
                        .daysUntilStockout(5)
                        .status(RecommendationStatus.PENDING)
                        .build(),
                OrderRecommendation.builder()
                        .sku("GUN-RG-001")
                        .productTitle("Gundam RG RX-78-2 Ver 2.0")
                        .currentStock(3)
                        .erpStock(3)
                        .shopifyStock(0)
                        .recommendedQuantity(30)
                        .reorderPoint(15)
                        .safetyStockLevel(10)
                        .leadTimeDays(21)
                        .supplierName("Bluefin Distribution")
                        .costPerUnit(new java.math.BigDecimal("32.99"))
                        .totalCost(new java.math.BigDecimal("989.70"))
                        .urgency(UrgencyLevel.CRITICAL)
                        .confidenceScore(new java.math.BigDecimal("0.88"))
                        .aiReasoning("URGENT: Best-selling Gundam kit with high velocity. Stock critically low.")
                        .estimatedStockoutDate(java.time.LocalDate.now().plusDays(1))
                        .daysUntilStockout(1)
                        .status(RecommendationStatus.PENDING)
                        .build(),
                OrderRecommendation.builder()
                        .sku("VAL-70101")
                        .productTitle("Vallejo Model Color Basic Set")
                        .currentStock(8)
                        .erpStock(8)
                        .shopifyStock(0)
                        .recommendedQuantity(20)
                        .reorderPoint(12)
                        .safetyStockLevel(8)
                        .leadTimeDays(10)
                        .supplierName("Acrylicos Vallejo")
                        .costPerUnit(new java.math.BigDecimal("24.99"))
                        .totalCost(new java.math.BigDecimal("499.80"))
                        .urgency(UrgencyLevel.MEDIUM)
                        .confidenceScore(new java.math.BigDecimal("0.75"))
                        .aiReasoning("Steady seller approaching reorder point. Moderate urgency.")
                        .estimatedStockoutDate(java.time.LocalDate.now().plusDays(4))
                        .daysUntilStockout(4)
                        .status(RecommendationStatus.PENDING)
                        .build()
        );
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
