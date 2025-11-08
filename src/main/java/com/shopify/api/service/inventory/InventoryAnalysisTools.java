package com.shopify.api.service.inventory;

import com.shopify.api.model.inventory.*;
import com.shopify.api.repository.inventory.OrderRecommendationRepository;
import com.shopify.api.repository.inventory.SalesVelocityRepository;
import com.shopify.api.repository.inventory.StockAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tool implementations for the Inventory Analysis AI Agent
 * Each method represents a tool the AI can call
 */
@Service
public class InventoryAnalysisTools {

    private static final Logger logger = LoggerFactory.getLogger(InventoryAnalysisTools.class);

    private final ERPInventoryService erpService;
    private final SalesVelocityRepository velocityRepository;
    private final StockAlertRepository alertRepository;
    private final OrderRecommendationRepository recommendationRepository;
    private final SalesVelocityCalculator velocityCalculator;

    public InventoryAnalysisTools(
            ERPInventoryService erpService,
            SalesVelocityRepository velocityRepository,
            StockAlertRepository alertRepository,
            OrderRecommendationRepository recommendationRepository,
            SalesVelocityCalculator velocityCalculator) {
        this.erpService = erpService;
        this.velocityRepository = velocityRepository;
        this.alertRepository = alertRepository;
        this.recommendationRepository = recommendationRepository;
        this.velocityCalculator = velocityCalculator;
    }

    /**
     * Tool 1: Analyze inventory by brand
     */
    public Map<String, Object> analyzeByBrand(String brand, int days) {
        logger.info("analyzeByBrand: brand={}, days={}", brand, days);

        Map<String, Object> result = new HashMap<>();
        result.put("brand", brand);
        result.put("days", days);

        try {
            // Get all velocities and filter by brand (simplified - would use ERP metadata)
            List<SalesVelocity> allVelocities = velocityRepository.findAll();

            // For now, filter by SKU prefix or name matching brand
            // In production, use ERP metadata
            List<SalesVelocity> brandVelocities = allVelocities.stream()
                    .filter(v -> v.getSku() != null && matchesBrand(v.getSku(), brand))
                    .collect(Collectors.toList());

            result.put("totalProducts", brandVelocities.size());

            // Count low stock items
            long lowStockCount = brandVelocities.stream()
                    .filter(v -> {
                        List<StockAlert> alerts = alertRepository.findBySkuOrderByCreatedAtDesc(v.getSku());
                        return !alerts.isEmpty() && alerts.stream().anyMatch(a -> !a.getResolved());
                    })
                    .count();

            result.put("lowStockCount", lowStockCount);

            // Calculate average velocity
            double avgVelocity = brandVelocities.stream()
                    .filter(v -> v.getDailyAverage() != null)
                    .mapToDouble(v -> v.getDailyAverage().doubleValue())
                    .average()
                    .orElse(0.0);

            result.put("averageVelocity", BigDecimal.valueOf(avgVelocity).setScale(2, RoundingMode.HALF_UP));

            // Get critical alerts for this brand
            List<StockAlert> criticalAlerts = alertRepository
                    .findByResolvedFalseAndAlertLevelOrderByCreatedAtDesc(AlertLevel.CRITICAL)
                    .stream()
                    .filter(a -> matchesBrand(a.getSku(), brand))
                    .limit(10)
                    .collect(Collectors.toList());

            result.put("criticalAlerts", criticalAlerts);

            // Get pending recommendations for this brand
            List<OrderRecommendation> recommendations = recommendationRepository
                    .findByStatus(RecommendationStatus.PENDING)
                    .stream()
                    .filter(r -> matchesBrand(r.getSku(), brand))
                    .limit(10)
                    .collect(Collectors.toList());

            result.put("recommendations", recommendations);

            result.put("success", true);

        } catch (Exception e) {
            logger.error("Error in analyzeByBrand: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Tool 2: Analyze inventory by category
     */
    public Map<String, Object> analyzeByCategory(String category, int days) {
        logger.info("analyzeByCategory: category={}, days={}", category, days);

        Map<String, Object> result = new HashMap<>();
        result.put("category", category);
        result.put("days", days);

        try {
            // Similar to analyzeByBrand, but filter by category
            List<SalesVelocity> allVelocities = velocityRepository.findAll();

            List<SalesVelocity> categoryVelocities = allVelocities.stream()
                    .filter(v -> v.getSku() != null && matchesCategory(v.getSku(), category))
                    .collect(Collectors.toList());

            result.put("totalProducts", categoryVelocities.size());

            long lowStockCount = categoryVelocities.stream()
                    .filter(v -> {
                        List<StockAlert> alerts = alertRepository.findBySkuOrderByCreatedAtDesc(v.getSku());
                        return !alerts.isEmpty() && alerts.stream().anyMatch(a -> !a.getResolved());
                    })
                    .count();

            result.put("lowStockCount", lowStockCount);

            double avgVelocity = categoryVelocities.stream()
                    .filter(v -> v.getDailyAverage() != null)
                    .mapToDouble(v -> v.getDailyAverage().doubleValue())
                    .average()
                    .orElse(0.0);

            result.put("averageVelocity", BigDecimal.valueOf(avgVelocity).setScale(2, RoundingMode.HALF_UP));

            result.put("success", true);

        } catch (Exception e) {
            logger.error("Error in analyzeByCategory: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Tool 3: Analyze inventory by supplier
     */
    public Map<String, Object> analyzeBySupplier(String supplier, int days) {
        logger.info("analyzeBySupplier: supplier={}, days={}", supplier, days);

        Map<String, Object> result = new HashMap<>();
        result.put("supplier", supplier);
        result.put("days", days);

        try {
            // Get recommendations for this supplier
            List<OrderRecommendation> supplierRecs = recommendationRepository
                    .findByStatus(RecommendationStatus.PENDING)
                    .stream()
                    .filter(r -> r.getSupplierName() != null &&
                                r.getSupplierName().equalsIgnoreCase(supplier))
                    .collect(Collectors.toList());

            result.put("totalProducts", supplierRecs.size());

            BigDecimal totalValue = supplierRecs.stream()
                    .map(r -> r.getTotalCost() != null ? r.getTotalCost() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            result.put("totalOrderValue", totalValue);

            long criticalCount = supplierRecs.stream()
                    .filter(r -> r.getUrgency() == UrgencyLevel.CRITICAL)
                    .count();

            result.put("criticalCount", criticalCount);

            result.put("recommendations", supplierRecs);
            result.put("success", true);

        } catch (Exception e) {
            logger.error("Error in analyzeBySupplier: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Tool 4: Get low stock items with filters
     */
    public Map<String, Object> getLowStockFiltered(Map<String, String> filters) {
        logger.info("getLowStockFiltered: filters={}", filters);

        Map<String, Object> result = new HashMap<>();

        try {
            String brand = filters.get("brand");
            String category = filters.get("category");
            String levelStr = filters.get("level");

            AlertLevel level = levelStr != null ?
                    AlertLevel.valueOf(levelStr.toUpperCase()) :
                    AlertLevel.WARNING;

            List<StockAlert> alerts = alertRepository
                    .findByResolvedFalseAndAlertLevelOrderByCreatedAtDesc(level);

            // Apply filters
            if (brand != null) {
                alerts = alerts.stream()
                        .filter(a -> matchesBrand(a.getSku(), brand))
                        .collect(Collectors.toList());
            }

            if (category != null) {
                alerts = alerts.stream()
                        .filter(a -> matchesCategory(a.getSku(), category))
                        .collect(Collectors.toList());
            }

            result.put("alerts", alerts);
            result.put("count", alerts.size());
            result.put("level", level);
            result.put("success", true);

        } catch (Exception e) {
            logger.error("Error in getLowStockFiltered: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Tool 5: Generate order plan for a product
     */
    public Map<String, Object> generateOrderPlan(String sku, int targetDays) {
        logger.info("generateOrderPlan: sku={}, targetDays={}", sku, targetDays);

        Map<String, Object> result = new HashMap<>();
        result.put("sku", sku);
        result.put("targetDays", targetDays);

        try {
            // Get sales velocity
            Optional<SalesVelocity> velocityOpt = velocityRepository.findBySku(sku);
            if (velocityOpt.isEmpty()) {
                result.put("success", false);
                result.put("error", "No velocity data for SKU: " + sku);
                return result;
            }

            SalesVelocity velocity = velocityOpt.get();
            BigDecimal dailyVelocity = velocity.getDailyAverage();

            if (dailyVelocity == null || dailyVelocity.compareTo(BigDecimal.ZERO) <= 0) {
                result.put("success", false);
                result.put("error", "Invalid velocity for SKU: " + sku);
                return result;
            }

            // Calculate quantity needed
            BigDecimal quantityNeeded = dailyVelocity.multiply(BigDecimal.valueOf(targetDays));
            int recommendedQuantity = quantityNeeded.setScale(0, RoundingMode.UP).intValue();

            // Get current stock (would come from ERP in production)
            int currentStock = 0; // Placeholder

            // Get cost (would come from ERP in production)
            BigDecimal unitCost = BigDecimal.valueOf(10.00); // Placeholder

            BigDecimal totalCost = unitCost.multiply(BigDecimal.valueOf(recommendedQuantity));

            result.put("sku", sku);
            result.put("currentStock", currentStock);
            result.put("dailyVelocity", dailyVelocity);
            result.put("recommendedQuantity", recommendedQuantity);
            result.put("unitCost", unitCost);
            result.put("totalCost", totalCost);
            result.put("supplier", "TBD"); // Would come from ERP
            result.put("success", true);

        } catch (Exception e) {
            logger.error("Error in generateOrderPlan: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Tool 6: Get supplier summary
     */
    public Map<String, Object> getSupplierSummary(String supplier) {
        logger.info("getSupplierSummary: supplier={}", supplier);

        Map<String, Object> result = new HashMap<>();
        result.put("supplier", supplier);

        try {
            List<OrderRecommendation> supplierRecs = recommendationRepository
                    .findAll()
                    .stream()
                    .filter(r -> r.getSupplierName() != null &&
                                r.getSupplierName().equalsIgnoreCase(supplier))
                    .collect(Collectors.toList());

            long pendingCount = supplierRecs.stream()
                    .filter(r -> r.getStatus() == RecommendationStatus.PENDING)
                    .count();

            BigDecimal pendingValue = supplierRecs.stream()
                    .filter(r -> r.getStatus() == RecommendationStatus.PENDING)
                    .map(r -> r.getTotalCost() != null ? r.getTotalCost() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            result.put("totalProducts", supplierRecs.size());
            result.put("pendingOrders", pendingCount);
            result.put("pendingValue", pendingValue);
            result.put("recommendations", supplierRecs);
            result.put("success", true);

        } catch (Exception e) {
            logger.error("Error in getSupplierSummary: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Tool 7: Predict stockout date
     */
    public Map<String, Object> predictStockout(String sku, int forecastDays) {
        logger.info("predictStockout: sku={}, forecastDays={}", sku, forecastDays);

        Map<String, Object> result = new HashMap<>();
        result.put("sku", sku);

        try {
            Optional<SalesVelocity> velocityOpt = velocityRepository.findBySku(sku);
            if (velocityOpt.isEmpty()) {
                result.put("success", false);
                result.put("error", "No velocity data for SKU: " + sku);
                return result;
            }

            SalesVelocity velocity = velocityOpt.get();
            BigDecimal dailyVelocity = velocity.getDailyAverage();

            if (dailyVelocity == null || dailyVelocity.compareTo(BigDecimal.ZERO) <= 0) {
                result.put("prediction", "No sales activity - stockout unlikely");
                result.put("success", true);
                return result;
            }

            // Current stock (would come from ERP)
            int currentStock = 100; // Placeholder

            // Days until stockout
            BigDecimal daysUntilStockout = BigDecimal.valueOf(currentStock)
                    .divide(dailyVelocity, 2, RoundingMode.HALF_UP);

            LocalDateTime stockoutDate = LocalDateTime.now()
                    .plusDays(daysUntilStockout.longValue());

            result.put("currentStock", currentStock);
            result.put("dailyVelocity", dailyVelocity);
            result.put("daysUntilStockout", daysUntilStockout);
            result.put("predictedStockoutDate", stockoutDate.toString());

            if (daysUntilStockout.compareTo(BigDecimal.valueOf(forecastDays)) <= 0) {
                result.put("willStockout", true);
                result.put("urgency", "HIGH");
            } else {
                result.put("willStockout", false);
                result.put("urgency", "LOW");
            }

            result.put("success", true);

        } catch (Exception e) {
            logger.error("Error in predictStockout: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Tool 8: Compare multiple products
     */
    public Map<String, Object> compareProducts(List<String> skuList) {
        logger.info("compareProducts: skus={}", skuList);

        Map<String, Object> result = new HashMap<>();
        result.put("skus", skuList);

        try {
            List<Map<String, Object>> comparisons = new ArrayList<>();

            for (String sku : skuList) {
                Map<String, Object> productData = new HashMap<>();
                productData.put("sku", sku);

                Optional<SalesVelocity> velocityOpt = velocityRepository.findBySku(sku);
                if (velocityOpt.isPresent()) {
                    SalesVelocity velocity = velocityOpt.get();
                    productData.put("dailyVelocity", velocity.getDailyAverage());
                    productData.put("weeklyVelocity", velocity.getWeeklyAverage());
                    productData.put("trend", velocity.getTrend());
                } else {
                    productData.put("error", "No velocity data");
                }

                // Get alerts
                List<StockAlert> alerts = alertRepository.findBySkuOrderByCreatedAtDesc(sku);
                productData.put("activeAlerts", alerts.stream()
                        .filter(a -> !a.getResolved())
                        .count());

                comparisons.add(productData);
            }

            result.put("comparisons", comparisons);
            result.put("success", true);

        } catch (Exception e) {
            logger.error("Error in compareProducts: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Helper: Check if SKU matches brand (simplified)
     * In production, this would query ERP metadata
     */
    private boolean matchesBrand(String sku, String brand) {
        if (sku == null || brand == null) return false;

        String skuUpper = sku.toUpperCase();
        String brandUpper = brand.toUpperCase();

        // Simple matching - check if brand name appears in SKU
        // In production, use ERP metadata
        return skuUpper.contains(brandUpper) ||
               skuUpper.startsWith(brandUpper.substring(0, Math.min(3, brandUpper.length())));
    }

    /**
     * Helper: Check if SKU matches category (simplified)
     */
    private boolean matchesCategory(String sku, String category) {
        if (sku == null || category == null) return false;

        String categoryUpper = category.toUpperCase();

        // Simple pattern matching
        // In production, use ERP metadata
        if (categoryUpper.contains("PAINT")) {
            return sku.toUpperCase().contains("PAINT") || sku.toUpperCase().contains("CLR");
        } else if (categoryUpper.contains("KIT") || categoryUpper.contains("MODEL")) {
            return sku.toUpperCase().contains("KIT") || sku.toUpperCase().contains("MDL");
        }

        return false;
    }
}
