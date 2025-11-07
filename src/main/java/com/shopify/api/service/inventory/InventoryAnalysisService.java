package com.shopify.api.service.inventory;

import com.shopify.api.model.inventory.*;
import com.shopify.api.repository.inventory.OrderRecommendationRepository;
import com.shopify.api.repository.inventory.SalesVelocityRepository;
import com.shopify.api.repository.inventory.StockAlertRepository;
import com.shopify.api.service.ChatAgentService;
import com.shopify.api.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Core service for inventory analysis and order recommendations
 * Orchestrates the entire analysis workflow:
 * 1. Fetch data from ERP (primary) and Shopify (cross-reference)
 * 2. Calculate sales velocity and trends
 * 3. Determine reorder points and quantities
 * 4. Generate AI-powered insights
 * 5. Create recommendations and alerts
 */
@Service
public class InventoryAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryAnalysisService.class);

    private final ERPInventoryService erpService;
    private final SalesVelocityCalculator velocityCalculator;
    private final SalesVelocityRepository velocityRepository;
    private final OrderRecommendationRepository recommendationRepository;
    private final StockAlertRepository alertRepository;
    private final ChatAgentService chatAgentService;
    private final ProductService productService;

    // Configuration
    private final boolean enabled;
    private final int criticalStockDays;
    private final int warningStockDays;
    private final int safetyStockDays;
    private final double safetyMultiplier;
    private final int velocityWindowDays;
    private final boolean aiEnabled;

    public InventoryAnalysisService(
            ERPInventoryService erpService,
            SalesVelocityCalculator velocityCalculator,
            SalesVelocityRepository velocityRepository,
            OrderRecommendationRepository recommendationRepository,
            StockAlertRepository alertRepository,
            ChatAgentService chatAgentService,
            ProductService productService,
            @Value("${inventory-analysis.enabled:true}") boolean enabled,
            @Value("${inventory-analysis.thresholds.critical-stock-days:3}") int criticalStockDays,
            @Value("${inventory-analysis.thresholds.warning-stock-days:7}") int warningStockDays,
            @Value("${inventory-analysis.safety.safety-stock-days:7}") int safetyStockDays,
            @Value("${inventory-analysis.safety.safety-multiplier:1.5}") double safetyMultiplier,
            @Value("${inventory-analysis.velocity.default-window-days:30}") int velocityWindowDays,
            @Value("${inventory-analysis.ai.enabled:true}") boolean aiEnabled) {

        this.erpService = erpService;
        this.velocityCalculator = velocityCalculator;
        this.velocityRepository = velocityRepository;
        this.recommendationRepository = recommendationRepository;
        this.alertRepository = alertRepository;
        this.chatAgentService = chatAgentService;
        this.productService = productService;
        this.enabled = enabled;
        this.criticalStockDays = criticalStockDays;
        this.warningStockDays = warningStockDays;
        this.safetyStockDays = safetyStockDays;
        this.safetyMultiplier = safetyMultiplier;
        this.velocityWindowDays = velocityWindowDays;
        this.aiEnabled = aiEnabled;

        logger.info("InventoryAnalysisService initialized - Enabled: {}, AI: {}, Critical: {}d, Warning: {}d",
                enabled, aiEnabled, criticalStockDays, warningStockDays);
    }

    /**
     * Analyze a single product comprehensively
     * This is the main entry point for product analysis
     *
     * @param sku Product SKU
     * @return Complete analysis result with AI insights
     */
    @Transactional
    public Mono<InventoryAnalysisResult> analyzeSingleProduct(String sku) {
        if (!enabled) {
            logger.warn("Inventory analysis disabled");
            return Mono.empty();
        }

        logger.info("Starting comprehensive analysis for SKU: {}", sku);

        // Fetch all data in parallel
        return Mono.zip(
                erpService.getSalesHistory(sku, velocityWindowDays),
                erpService.getInventoryLevel(sku),
                erpService.getSupplierInfo(sku),
                erpService.getProductCost(sku)
        ).flatMap(tuple -> {
            List<SaleRecord> salesHistory = tuple.getT1();
            Integer erpStock = tuple.getT2();
            SupplierInfo supplierInfo = tuple.getT3();
            BigDecimal cost = tuple.getT4();

            logger.debug("Data fetched for SKU {}: {} sales records, stock={}, leadTime={}d",
                    sku, salesHistory.size(), erpStock, supplierInfo.getLeadTimeDays());

            // Calculate sales velocity
            SalesVelocity calculatedVelocity = velocityCalculator.calculateDetailed(salesHistory, sku);
            calculatedVelocity.setProductId(sku); // Can be enhanced with actual Shopify product ID

            // Save or update velocity
            velocityRepository.findBySku(sku).ifPresent(existing -> calculatedVelocity.setId(existing.getId()));
            SalesVelocity velocity = velocityRepository.save(calculatedVelocity);

            // Calculate metrics
            int reorderPoint = calculateReorderPoint(velocity, supplierInfo.getLeadTimeDays());
            int safetyStock = calculateSafetyStock(velocity);
            int recommendedQuantity = calculateOrderQuantity(velocity, erpStock, reorderPoint, supplierInfo);
            Integer daysUntilStockout = calculateDaysUntilStockout(erpStock, velocity);
            LocalDate stockoutDate = daysUntilStockout != null ?
                    LocalDate.now().plusDays(daysUntilStockout) : null;

            // Determine urgency
            UrgencyLevel urgency = determineUrgency(daysUntilStockout);

            // Build analysis result
            InventoryAnalysisResult result = InventoryAnalysisResult.builder()
                    .sku(sku)
                    .productTitle("Product " + sku) // TODO: Get from Shopify
                    .shopifyStock(null) // TODO: Get from Shopify for cross-reference
                    .erpStock(erpStock)
                    .totalAvailableStock(erpStock)
                    .velocity(velocity)
                    .supplierName(supplierInfo.getSupplierName())
                    .leadTimeDays(supplierInfo.getLeadTimeDays())
                    .costPerUnit(cost)
                    .reorderPoint(reorderPoint)
                    .safetyStockLevel(safetyStock)
                    .recommendedOrderQuantity(recommendedQuantity)
                    .daysUntilStockout(daysUntilStockout)
                    .estimatedStockoutDate(stockoutDate)
                    .urgencyLevel(urgency)
                    .confidenceScore(calculateConfidenceScore(salesHistory.size(), velocity))
                    .analyzedAt(LocalDateTime.now())
                    .analysisVersion("1.0.0")
                    .calculationPeriodDays(velocityWindowDays)
                    .build();

            // Generate AI insights if enabled
            return generateAIInsights(result)
                    .map(aiInsights -> {
                        result.setAiAnalysis(aiInsights);
                        return result;
                    })
                    .defaultIfEmpty(result);
        }).doOnSuccess(result -> {
            logger.info("Analysis complete for SKU {}: Stock={}, Reorder={}, Days until stockout={}",
                    sku, result.getTotalAvailableStock(), result.getReorderPoint(), result.getDaysUntilStockout());

            // Create recommendation if needed
            if (result.isBelowReorderPoint() || result.needsImmediateAction()) {
                createRecommendation(result).subscribe();
            }

            // Create alert if critical
            if (result.getDaysUntilStockout() != null && result.getDaysUntilStockout() <= criticalStockDays) {
                createAlert(result).subscribe();
            }
        }).doOnError(error -> {
            logger.error("Error analyzing SKU {}: {}", sku, error.getMessage(), error);
        });
    }

    /**
     * Find all products with low stock
     *
     * @param minimumLevel Minimum alert level to return
     * @return Flux of stock alerts
     */
    public Flux<StockAlert> findLowStockProducts(AlertLevel minimumLevel) {
        logger.info("Finding low stock products with minimum level: {}", minimumLevel);

        return Flux.fromIterable(
                alertRepository.findByResolvedFalseAndAlertLevelOrderByCreatedAtDesc(minimumLevel)
        );
    }

    /**
     * Generate order recommendations for all products needing reorder
     *
     * @return Flux of pending recommendations
     */
    public Flux<OrderRecommendation> generateRecommendations() {
        logger.info("Fetching pending order recommendations");

        return Flux.fromIterable(
                recommendationRepository.findByStatus(RecommendationStatus.PENDING)
        );
    }

    /**
     * Get dashboard summary data
     *
     * @return Dashboard data
     */
    public Mono<InventoryDashboard> getDashboardData() {
        logger.info("Generating dashboard data");

        return Mono.fromCallable(() -> {
            // Summary statistics
            long totalProducts = velocityRepository.count();
            long trackedProducts = velocityRepository.count();
            long lowStockCount = alertRepository.countByResolvedFalseAndAlertLevel(AlertLevel.WARNING);
            long criticalStockCount = alertRepository.countByResolvedFalseAndAlertLevel(AlertLevel.CRITICAL);
            long pendingRecs = recommendationRepository.countByStatus(RecommendationStatus.PENDING);
            long approvedRecs = recommendationRepository.countByStatus(RecommendationStatus.APPROVED);
            BigDecimal totalValue = recommendationRepository.getTotalPendingOrderValue();

            InventoryDashboard.SummaryStats summary = InventoryDashboard.SummaryStats.builder()
                    .totalProducts((int) totalProducts)
                    .trackedProducts((int) trackedProducts)
                    .lowStockCount((int) lowStockCount)
                    .criticalStockCount((int) criticalStockCount)
                    .pendingRecommendations((int) pendingRecs)
                    .approvedRecommendations((int) approvedRecs)
                    .totalRecommendedOrderValue(totalValue)
                    .build();

            // Inventory health breakdown (simplified for now)
            InventoryDashboard.HealthBreakdown health = InventoryDashboard.HealthBreakdown.builder()
                    .healthy((int) (totalProducts - lowStockCount - criticalStockCount))
                    .fair(0)
                    .warning((int) lowStockCount)
                    .critical((int) criticalStockCount)
                    .outOfStock(0)
                    .build();

            // Top alerts
            List<StockAlert> topAlerts = alertRepository.findCriticalUnresolvedAlerts()
                    .stream()
                    .limit(10)
                    .toList();

            // Recent recommendations
            List<OrderRecommendation> recentRecs = recommendationRepository
                    .findByStatus(RecommendationStatus.PENDING)
                    .stream()
                    .limit(10)
                    .toList();

            // Sales metrics (simplified)
            InventoryDashboard.SalesMetrics salesMetrics = InventoryDashboard.SalesMetrics.builder()
                    .totalSalesLast30Days(0)
                    .averageDailySales(BigDecimal.ZERO)
                    .totalRevenueLast30Days(BigDecimal.ZERO)
                    .topSellingProducts(List.of())
                    .build();

            return InventoryDashboard.builder()
                    .summary(summary)
                    .inventoryHealth(health)
                    .topCriticalAlerts(topAlerts)
                    .recentRecommendations(recentRecs)
                    .salesMetrics(salesMetrics)
                    .generatedAt(LocalDateTime.now())
                    .generatedBy("InventoryAnalysisService")
                    .build();
        });
    }

    // =====================================================
    // Calculation Methods
    // =====================================================

    /**
     * Calculate reorder point
     * Formula: (Daily Velocity × Lead Time Days) + Safety Stock
     */
    private int calculateReorderPoint(SalesVelocity velocity, int leadTimeDays) {
        if (velocity.getDailyAverage() == null) {
            return 0;
        }

        double dailyVelocity = velocity.getDailyAverage().doubleValue();
        int safetyStock = calculateSafetyStock(velocity);

        int reorderPoint = (int) Math.ceil((dailyVelocity * leadTimeDays) + safetyStock);

        logger.debug("Reorder point calculated: dailyVelocity={}, leadTime={}d, safety={}, result={}",
                dailyVelocity, leadTimeDays, safetyStock, reorderPoint);

        return Math.max(reorderPoint, 1);
    }

    /**
     * Calculate safety stock
     * Formula: Daily Velocity × Safety Days × Safety Multiplier
     */
    private int calculateSafetyStock(SalesVelocity velocity) {
        if (velocity.getDailyAverage() == null) {
            return 0;
        }

        double dailyVelocity = velocity.getDailyAverage().doubleValue();
        int safetyStock = (int) Math.ceil(dailyVelocity * safetyStockDays * safetyMultiplier);

        return Math.max(safetyStock, 1);
    }

    /**
     * Calculate optimal order quantity
     * Accounts for current stock, reorder point, supplier constraints
     */
    private int calculateOrderQuantity(SalesVelocity velocity, int currentStock,
                                       int reorderPoint, SupplierInfo supplierInfo) {
        if (currentStock >= reorderPoint) {
            return 0; // No order needed
        }

        double dailyVelocity = velocity.getDailyAverage() != null ?
                velocity.getDailyAverage().doubleValue() : 0.0;

        // Order enough to last 30 days plus safety stock
        int desiredQuantity = (int) Math.ceil((dailyVelocity * 30) + calculateSafetyStock(velocity) - currentStock);

        // Apply supplier constraints
        return supplierInfo.calculateActualOrderQuantity(Math.max(desiredQuantity, 1));
    }

    /**
     * Calculate days until stockout
     * Returns null if no stockout predicted or velocity is zero
     */
    private Integer calculateDaysUntilStockout(int currentStock, SalesVelocity velocity) {
        if (currentStock <= 0) {
            return 0; // Already out of stock
        }

        if (velocity.getDailyAverage() == null || velocity.getDailyAverage().compareTo(BigDecimal.ZERO) <= 0) {
            return null; // No sales velocity, can't predict
        }

        double dailyVelocity = velocity.getDailyAverage().doubleValue();
        int days = (int) Math.floor(currentStock / dailyVelocity);

        return days;
    }

    /**
     * Determine urgency level based on days until stockout
     */
    private UrgencyLevel determineUrgency(Integer daysUntilStockout) {
        if (daysUntilStockout == null) {
            return UrgencyLevel.LOW;
        }

        if (daysUntilStockout <= criticalStockDays) {
            return UrgencyLevel.CRITICAL;
        } else if (daysUntilStockout <= warningStockDays) {
            return UrgencyLevel.HIGH;
        } else if (daysUntilStockout <= 14) {
            return UrgencyLevel.MEDIUM;
        } else {
            return UrgencyLevel.LOW;
        }
    }

    /**
     * Calculate confidence score for recommendation
     * Based on sample size and data quality
     */
    private BigDecimal calculateConfidenceScore(int sampleSize, SalesVelocity velocity) {
        // More samples = higher confidence
        double sizeScore = Math.min(sampleSize / 30.0, 1.0);

        // Stable trend = higher confidence
        double trendScore = velocity.getTrend() == TrendDirection.STABLE ? 1.0 : 0.8;

        double confidence = (sizeScore + trendScore) / 2.0;

        return BigDecimal.valueOf(confidence).setScale(2, RoundingMode.HALF_UP);
    }

    // =====================================================
    // AI Integration
    // =====================================================

    /**
     * Generate AI-powered insights using Claude
     */
    private Mono<String> generateAIInsights(InventoryAnalysisResult result) {
        if (!aiEnabled) {
            logger.debug("AI insights disabled");
            return Mono.just("AI insights disabled");
        }

        String prompt = buildAnalysisPrompt(result);

        // Use ChatAgentService to get AI insights
        // Note: ChatAgentService.processChat expects ChatRequest, so we'll use a simple approach
        return Mono.just("AI insights generation integrated with ChatAgentService - implementation pending")
                .doOnSuccess(insights -> logger.debug("AI insights generated for SKU: {}", result.getSku()))
                .onErrorResume(error -> {
                    logger.error("Error generating AI insights: {}", error.getMessage());
                    return Mono.just("AI insights unavailable: " + error.getMessage());
                });
    }

    /**
     * Build prompt for AI analysis
     */
    private String buildAnalysisPrompt(InventoryAnalysisResult result) {
        return String.format("""
                You are an inventory management expert for Hearn's Hobbies, a specialty hobby store.

                Analyze this product's inventory situation and provide recommendations:

                PRODUCT INFORMATION:
                - SKU: %s
                - Title: %s

                CURRENT STOCK LEVELS:
                - ERP Warehouse: %d units
                - Total Available: %d units

                SALES VELOCITY (Last %d days):
                - Daily Average: %s units/day
                - Weekly Average: %s units/week
                - Monthly Average: %s units/month
                - Trend: %s (%s%%)
                - Sample Size: %d transactions

                SUPPLIER INFORMATION:
                - Supplier: %s
                - Lead Time: %d days
                - Cost Per Unit: $%s

                CALCULATED METRICS:
                - Reorder Point: %d units
                - Days Until Stockout: %s days
                - Estimated Stockout Date: %s

                TASK:
                Provide a concise analysis (3-4 sentences) covering:
                1. Current situation assessment and urgency
                2. Specific ordering recommendation with quantity
                3. Key risk factors or opportunities

                Be specific, data-driven, and actionable.
                """,
                result.getSku(),
                result.getProductTitle(),
                result.getErpStock(),
                result.getTotalAvailableStock(),
                result.getCalculationPeriodDays(),
                result.getVelocity().getDailyAverage(),
                result.getVelocity().getWeeklyAverage(),
                result.getVelocity().getMonthlyAverage(),
                result.getVelocity().getTrend(),
                result.getVelocity().getTrendPercentage(),
                result.getVelocity().getDataSampleSize(),
                result.getSupplierName(),
                result.getLeadTimeDays(),
                result.getCostPerUnit(),
                result.getReorderPoint(),
                result.getDaysUntilStockout(),
                result.getEstimatedStockoutDate()
        );
    }

    // =====================================================
    // Recommendation and Alert Creation
    // =====================================================

    /**
     * Create order recommendation
     */
    @Transactional
    protected Mono<OrderRecommendation> createRecommendation(InventoryAnalysisResult result) {
        return Mono.fromCallable(() -> {
            OrderRecommendation recommendation = OrderRecommendation.builder()
                    .sku(result.getSku())
                    .productTitle(result.getProductTitle())
                    .currentStock(result.getTotalAvailableStock())
                    .erpStock(result.getErpStock())
                    .shopifyStock(result.getShopifyStock())
                    .recommendedQuantity(result.getRecommendedOrderQuantity())
                    .reorderPoint(result.getReorderPoint())
                    .safetyStockLevel(result.getSafetyStockLevel())
                    .leadTimeDays(result.getLeadTimeDays())
                    .supplierName(result.getSupplierName())
                    .costPerUnit(result.getCostPerUnit())
                    .totalCost(result.getRecommendedOrderCost())
                    .urgency(result.getUrgencyLevel())
                    .confidenceScore(result.getConfidenceScore())
                    .aiReasoning(result.getAiAnalysis())
                    .recommendations(result.getRecommendations())
                    .estimatedStockoutDate(result.getEstimatedStockoutDate())
                    .daysUntilStockout(result.getDaysUntilStockout())
                    .status(RecommendationStatus.PENDING)
                    .velocityId(result.getVelocity() != null ? result.getVelocity().getId() : null)
                    .build();

            return recommendationRepository.save(recommendation);
        }).doOnSuccess(rec -> logger.info("Created recommendation {} for SKU: {}", rec.getId(), rec.getSku()));
    }

    /**
     * Create stock alert
     */
    @Transactional
    protected Mono<StockAlert> createAlert(InventoryAnalysisResult result) {
        return Mono.fromCallable(() -> {
            AlertLevel level = result.getDaysUntilStockout() <= criticalStockDays ?
                    AlertLevel.CRITICAL : AlertLevel.WARNING;

            String message = StockAlert.generateMessage(
                    level,
                    result.getDaysUntilStockout(),
                    result.getProductTitle()
            );

            StockAlert alert = StockAlert.builder()
                    .sku(result.getSku())
                    .productTitle(result.getProductTitle())
                    .currentStock(result.getTotalAvailableStock())
                    .reorderPoint(result.getReorderPoint())
                    .alertLevel(level)
                    .daysUntilStockout(result.getDaysUntilStockout())
                    .estimatedStockoutDate(result.getEstimatedStockoutDate())
                    .message(message)
                    .resolved(false)
                    .build();

            return alertRepository.save(alert);
        }).doOnSuccess(alert -> logger.info("Created {} alert {} for SKU: {}",
                alert.getAlertLevel(), alert.getId(), alert.getSku()));
    }

    /**
     * Check if service is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}
