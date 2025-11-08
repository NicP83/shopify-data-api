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
     * Uses real-time ERP data via MCP for accurate analysis
     */
    public Map<String, Object> analyzeByCategory(String category, int days) {
        logger.info("analyzeByCategory: category={}, days={} - Using real-time ERP data", category, days);

        Map<String, Object> result = new HashMap<>();
        result.put("category", category);
        result.put("days", days);

        try {
            // Get SKUs from local database (these have been analyzed before)
            // In production, this would call MCP to get all SKUs in category from ERP
            List<SalesVelocity> allVelocities = velocityRepository.findAll();

            List<String> categorySkus = allVelocities.stream()
                    .filter(v -> v.getSku() != null && matchesCategory(v.getSku(), category))
                    .map(SalesVelocity::getSku)
                    .collect(Collectors.toList());

            logger.info("Found {} SKUs in category '{}' from database", categorySkus.size(), category);

            if (categorySkus.isEmpty()) {
                result.put("totalProducts", 0);
                result.put("lowStockCount", 0);
                result.put("averageVelocity", BigDecimal.ZERO);
                result.put("message", "No products found in category. Note: Category analysis requires products to be analyzed first.");
                result.put("success", true);
                return result;
            }

            // For each SKU, fetch FRESH data from ERP via MCP
            List<Map<String, Object>> productAnalysis = new ArrayList<>();
            int lowStockCount = 0;
            BigDecimal totalVelocity = BigDecimal.ZERO;
            int validVelocityCount = 0;

            for (String sku : categorySkus) {
                try {
                    // Get REAL current stock from ERP
                    Integer currentStock = erpService.getInventoryLevel(sku).block();

                    // Get REAL sales history from ERP
                    List<SaleRecord> salesHistory = erpService.getSalesHistory(sku, days).block();

                    if (salesHistory != null && !salesHistory.isEmpty()) {
                        SalesVelocity velocity = velocityCalculator.calculateDetailed(salesHistory, sku);
                        BigDecimal dailyVelocity = velocity.getDailyAverage();

                        if (dailyVelocity != null && dailyVelocity.compareTo(BigDecimal.ZERO) > 0) {
                            totalVelocity = totalVelocity.add(dailyVelocity);
                            validVelocityCount++;

                            // Check if low stock based on REAL ERP data
                            BigDecimal daysOfStock = currentStock != null && currentStock > 0 ?
                                BigDecimal.valueOf(currentStock).divide(dailyVelocity, 2, RoundingMode.HALF_UP) :
                                BigDecimal.ZERO;

                            if (daysOfStock.compareTo(BigDecimal.valueOf(7)) < 0) {
                                lowStockCount++;
                            }

                            Map<String, Object> productData = new HashMap<>();
                            productData.put("sku", sku);
                            productData.put("currentStock", currentStock);
                            productData.put("dailyVelocity", dailyVelocity);
                            productData.put("daysOfStock", daysOfStock);
                            productAnalysis.add(productData);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error analyzing SKU {} in category: {}", sku, e.getMessage());
                }
            }

            BigDecimal avgVelocity = validVelocityCount > 0 ?
                totalVelocity.divide(BigDecimal.valueOf(validVelocityCount), 2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

            result.put("totalProducts", categorySkus.size());
            result.put("lowStockCount", lowStockCount);
            result.put("averageVelocity", avgVelocity);
            result.put("products", productAnalysis);
            result.put("dataSource", "ERP via MCP");
            result.put("success", true);

            logger.info("Category analysis complete: {} products, {} low stock, avg velocity {}",
                categorySkus.size(), lowStockCount, avgVelocity);

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
     * Uses ONLY objective data from MCP/ERP - no invention!
     */
    public Map<String, Object> generateOrderPlan(String sku, int targetDays) {
        logger.info("generateOrderPlan: sku={}, targetDays={} - Fetching real ERP data via MCP", sku, targetDays);

        Map<String, Object> result = new HashMap<>();
        result.put("sku", sku);
        result.put("targetDays", targetDays);

        try {
            // Get real data from ERP via MCP
            logger.info("Calling MCP to get sales history for SKU: {}", sku);
            List<SaleRecord> salesHistory = erpService.getSalesHistory(sku, Math.max(targetDays, 30)).block();

            if (salesHistory == null || salesHistory.isEmpty()) {
                result.put("success", false);
                result.put("error", "No sales history available in ERP for SKU: " + sku);
                return result;
            }

            // Calculate velocity from REAL ERP sales data
            SalesVelocity velocity = velocityCalculator.calculateDetailed(salesHistory, sku);
            BigDecimal dailyVelocity = velocity.getDailyAverage();

            if (dailyVelocity == null || dailyVelocity.compareTo(BigDecimal.ZERO) <= 0) {
                result.put("success", false);
                result.put("error", "No sales velocity (0 sales) for SKU: " + sku);
                return result;
            }

            // Get REAL current stock from ERP
            logger.info("Calling MCP to get inventory level for SKU: {}", sku);
            Integer currentStock = erpService.getInventoryLevel(sku).block();
            if (currentStock == null) currentStock = 0;

            // Get REAL cost from ERP
            logger.info("Calling MCP to get product cost for SKU: {}", sku);
            BigDecimal unitCost = erpService.getProductCost(sku).block();
            if (unitCost == null) unitCost = BigDecimal.ZERO;

            // Get REAL supplier info from ERP
            logger.info("Calling MCP to get supplier info for SKU: {}", sku);
            SupplierInfo supplierInfo = erpService.getSupplierInfo(sku).block();
            String supplierName = supplierInfo != null ? supplierInfo.getSupplierName() : "Unknown";

            // Calculate quantity needed based on REAL ERP data
            BigDecimal quantityNeeded = dailyVelocity.multiply(BigDecimal.valueOf(targetDays));
            int recommendedQuantity = quantityNeeded.setScale(0, RoundingMode.UP).intValue();

            BigDecimal totalCost = unitCost.multiply(BigDecimal.valueOf(recommendedQuantity));

            result.put("sku", sku);
            result.put("currentStock", currentStock);
            result.put("dailyVelocity", dailyVelocity);
            result.put("weeklyVelocity", velocity.getWeeklyAverage());
            result.put("monthlyVelocity", velocity.getMonthlyAverage());
            result.put("trend", velocity.getTrend().toString());
            result.put("recommendedQuantity", recommendedQuantity);
            result.put("unitCost", unitCost);
            result.put("totalCost", totalCost);
            result.put("supplier", supplierName);
            result.put("leadTimeDays", supplierInfo != null ? supplierInfo.getLeadTimeDays() : null);
            result.put("dataSource", "ERP via MCP");
            result.put("success", true);

            logger.info("Generated order plan from ERP data: {} units @ ${} = ${}",
                recommendedQuantity, unitCost, totalCost);

        } catch (Exception e) {
            logger.error("Error in generateOrderPlan for SKU {}: {}", sku, e.getMessage(), e);
            result.put("success", false);
            result.put("error", "Failed to fetch ERP data: " + e.getMessage());
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
     * Uses ONLY real ERP data - no invented stock levels!
     */
    public Map<String, Object> predictStockout(String sku, int forecastDays) {
        logger.info("predictStockout: sku={}, forecastDays={} - Using real ERP data", sku, forecastDays);

        Map<String, Object> result = new HashMap<>();
        result.put("sku", sku);

        try {
            // Get REAL sales history from ERP
            logger.info("Calling MCP to get sales history for SKU: {}", sku);
            List<SaleRecord> salesHistory = erpService.getSalesHistory(sku, 30).block();

            if (salesHistory == null || salesHistory.isEmpty()) {
                result.put("success", false);
                result.put("error", "No sales history in ERP for SKU: " + sku);
                return result;
            }

            // Calculate velocity from REAL ERP data
            SalesVelocity velocity = velocityCalculator.calculateDetailed(salesHistory, sku);
            BigDecimal dailyVelocity = velocity.getDailyAverage();

            if (dailyVelocity == null || dailyVelocity.compareTo(BigDecimal.ZERO) <= 0) {
                result.put("prediction", "No sales activity in ERP - stockout unlikely");
                result.put("dataSource", "ERP via MCP");
                result.put("success", true);
                return result;
            }

            // Get REAL current stock from ERP
            logger.info("Calling MCP to get current stock for SKU: {}", sku);
            Integer currentStock = erpService.getInventoryLevel(sku).block();
            if (currentStock == null) {
                result.put("success", false);
                result.put("error", "No inventory level data in ERP for SKU: " + sku);
                return result;
            }

            // Days until stockout based on REAL ERP data
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

            result.put("dataSource", "ERP via MCP");
            result.put("success", true);

            logger.info("Stockout prediction from ERP: {} units, {} days until stockout",
                currentStock, daysUntilStockout);

        } catch (Exception e) {
            logger.error("Error in predictStockout for SKU {}: {}", sku, e.getMessage(), e);
            result.put("success", false);
            result.put("error", "Failed to fetch ERP data: " + e.getMessage());
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
     * Tool 9: Generate bulk order plan with multiple filters
     * Combines brand, category, and supplier filters to generate comprehensive order plans
     * Uses real-time ERP data via MCP
     */
    public Map<String, Object> generateBulkOrderPlan(String brand, String category, String supplier, int targetDays) {
        logger.info("generateBulkOrderPlan: brand={}, category={}, supplier={}, targetDays={} - Using ERP data via MCP",
                brand, category, supplier, targetDays);

        Map<String, Object> result = new HashMap<>();
        result.put("brand", brand);
        result.put("category", category);
        result.put("supplier", supplier);
        result.put("targetDays", targetDays);

        try {
            // Step 1: Get all SKUs from database that match brand/category filters
            List<SalesVelocity> allVelocities = velocityRepository.findAll();

            List<String> candidateSkus = allVelocities.stream()
                    .filter(v -> v.getSku() != null)
                    .filter(v -> brand == null || brand.equals("Unknown") || matchesBrand(v.getSku(), brand))
                    .filter(v -> category == null || category.equals("Unknown") || matchesCategory(v.getSku(), category))
                    .map(SalesVelocity::getSku)
                    .collect(Collectors.toList());

            logger.info("Found {} candidate SKUs matching brand '{}' and category '{}'",
                    candidateSkus.size(), brand, category);

            if (candidateSkus.isEmpty()) {
                result.put("totalProducts", 0);
                result.put("totalOrderValue", BigDecimal.ZERO);
                result.put("message", "No products found matching filters. Products may need to be analyzed first.");
                result.put("success", true);
                return result;
            }

            // Step 2: For each SKU, get real-time data from ERP and generate order plan
            List<Map<String, Object>> orderItems = new ArrayList<>();
            BigDecimal grandTotal = BigDecimal.ZERO;
            int totalQuantity = 0;

            for (String sku : candidateSkus) {
                try {
                    // Get real sales history from ERP
                    List<SaleRecord> salesHistory = erpService.getSalesHistory(sku, Math.max(targetDays, 30)).block();

                    if (salesHistory == null || salesHistory.isEmpty()) {
                        logger.debug("Skipping SKU {} - no sales history in ERP", sku);
                        continue;
                    }

                    // Calculate velocity from real ERP data
                    SalesVelocity velocity = velocityCalculator.calculateDetailed(salesHistory, sku);
                    BigDecimal dailyVelocity = velocity.getDailyAverage();

                    if (dailyVelocity == null || dailyVelocity.compareTo(BigDecimal.ZERO) <= 0) {
                        logger.debug("Skipping SKU {} - zero velocity", sku);
                        continue;
                    }

                    // Get real current stock from ERP
                    Integer currentStock = erpService.getInventoryLevel(sku).block();
                    if (currentStock == null) currentStock = 0;

                    // Get real cost from ERP
                    BigDecimal unitCost = erpService.getProductCost(sku).block();
                    if (unitCost == null) unitCost = BigDecimal.ZERO;

                    // Get real supplier info from ERP
                    SupplierInfo supplierInfo = erpService.getSupplierInfo(sku).block();
                    String actualSupplier = supplierInfo != null ? supplierInfo.getSupplierName() : "Unknown";

                    // Filter by supplier if specified
                    if (supplier != null && !supplier.equals("Unknown") &&
                            !actualSupplier.equalsIgnoreCase(supplier)) {
                        logger.debug("Skipping SKU {} - supplier mismatch (wanted: {}, actual: {})",
                                sku, supplier, actualSupplier);
                        continue;
                    }

                    // Calculate days of stock remaining
                    BigDecimal daysOfStock = currentStock > 0 ?
                            BigDecimal.valueOf(currentStock).divide(dailyVelocity, 2, RoundingMode.HALF_UP) :
                            BigDecimal.ZERO;

                    // Only include items that are low stock (< 14 days) or will need reordering soon
                    if (daysOfStock.compareTo(BigDecimal.valueOf(14)) >= 0) {
                        logger.debug("Skipping SKU {} - sufficient stock ({} days)", sku, daysOfStock);
                        continue;
                    }

                    // Calculate quantity needed for target days
                    BigDecimal quantityNeeded = dailyVelocity.multiply(BigDecimal.valueOf(targetDays));
                    int recommendedQuantity = quantityNeeded.setScale(0, RoundingMode.UP).intValue();

                    BigDecimal itemTotal = unitCost.multiply(BigDecimal.valueOf(recommendedQuantity));

                    // Determine urgency
                    String urgency;
                    if (daysOfStock.compareTo(BigDecimal.valueOf(3)) < 0) {
                        urgency = "CRITICAL";
                    } else if (daysOfStock.compareTo(BigDecimal.valueOf(7)) < 0) {
                        urgency = "HIGH";
                    } else {
                        urgency = "MEDIUM";
                    }

                    // Build order item
                    Map<String, Object> orderItem = new HashMap<>();
                    orderItem.put("sku", sku);
                    orderItem.put("supplier", actualSupplier);
                    orderItem.put("currentStock", currentStock);
                    orderItem.put("daysOfStock", daysOfStock);
                    orderItem.put("dailyVelocity", dailyVelocity);
                    orderItem.put("recommendedQuantity", recommendedQuantity);
                    orderItem.put("unitCost", unitCost);
                    orderItem.put("totalCost", itemTotal);
                    orderItem.put("urgency", urgency);
                    orderItem.put("leadTimeDays", supplierInfo != null ? supplierInfo.getLeadTimeDays() : null);

                    orderItems.add(orderItem);
                    grandTotal = grandTotal.add(itemTotal);
                    totalQuantity += recommendedQuantity;

                    logger.debug("Added order item: {} - {} units @ ${} = ${}",
                            sku, recommendedQuantity, unitCost, itemTotal);

                } catch (Exception e) {
                    logger.warn("Error processing SKU {} for bulk order: {}", sku, e.getMessage());
                }
            }

            // Step 3: Group by supplier for summary
            Map<String, List<Map<String, Object>>> bySupplier = orderItems.stream()
                    .collect(Collectors.groupingBy(item -> (String) item.get("supplier")));

            Map<String, Map<String, Object>> supplierSummaries = new HashMap<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : bySupplier.entrySet()) {
                String sup = entry.getKey();
                List<Map<String, Object>> items = entry.getValue();

                BigDecimal supplierTotal = items.stream()
                        .map(item -> (BigDecimal) item.get("totalCost"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                int supplierQuantity = items.stream()
                        .mapToInt(item -> (Integer) item.get("recommendedQuantity"))
                        .sum();

                Map<String, Object> summary = new HashMap<>();
                summary.put("totalCost", supplierTotal);
                summary.put("totalQuantity", supplierQuantity);
                summary.put("productCount", items.size());

                supplierSummaries.put(sup, summary);
            }

            // Sort order items by urgency (CRITICAL -> HIGH -> MEDIUM)
            orderItems.sort((a, b) -> {
                String urgencyA = (String) a.get("urgency");
                String urgencyB = (String) b.get("urgency");
                int priorityA = urgencyA.equals("CRITICAL") ? 3 : urgencyA.equals("HIGH") ? 2 : 1;
                int priorityB = urgencyB.equals("CRITICAL") ? 3 : urgencyB.equals("HIGH") ? 2 : 1;
                return Integer.compare(priorityB, priorityA); // Descending
            });

            result.put("totalProducts", orderItems.size());
            result.put("totalOrderValue", grandTotal);
            result.put("totalQuantity", totalQuantity);
            result.put("orderItems", orderItems);
            result.put("supplierSummaries", supplierSummaries);
            result.put("dataSource", "ERP via MCP");
            result.put("success", true);

            logger.info("Bulk order plan complete: {} products, {} units, ${} total",
                    orderItems.size(), totalQuantity, grandTotal);

        } catch (Exception e) {
            logger.error("Error in generateBulkOrderPlan: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", "Failed to generate bulk order plan: " + e.getMessage());
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
