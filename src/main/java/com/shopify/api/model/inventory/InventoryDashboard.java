package com.shopify.api.model.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO representing dashboard summary data
 * Provides high-level overview of inventory health
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDashboard {

    // Summary statistics
    private SummaryStats summary;

    // Inventory health breakdown
    private HealthBreakdown inventoryHealth;

    // Top alerts and recommendations
    private List<StockAlert> topCriticalAlerts;
    private List<OrderRecommendation> recentRecommendations;

    // Sales metrics
    private SalesMetrics salesMetrics;

    // Metadata
    private LocalDateTime generatedAt;
    private String generatedBy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryStats {
        private Integer totalProducts;
        private Integer trackedProducts; // Products with inventory tracking
        private Integer lowStockCount;
        private Integer criticalStockCount;
        private Integer pendingRecommendations;
        private Integer approvedRecommendations;
        private BigDecimal totalRecommendedOrderValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthBreakdown {
        private Integer healthy; // Good stock levels
        private Integer fair; // Adequate stock
        private Integer warning; // Low stock
        private Integer critical; // Critical stock
        private Integer outOfStock; // No stock

        public Integer getTotal() {
            return healthy + fair + warning + critical + outOfStock;
        }

        public Map<String, Integer> toMap() {
            return Map.of(
                    "healthy", healthy,
                    "fair", fair,
                    "warning", warning,
                    "critical", critical,
                    "outOfStock", outOfStock
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesMetrics {
        private Integer totalSalesLast30Days;
        private BigDecimal averageDailySales;
        private BigDecimal totalRevenueLast30Days;
        private List<TopSellingProduct> topSellingProducts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopSellingProduct {
        private String sku;
        private String productTitle;
        private Integer unitsSold;
        private BigDecimal revenue;
        private BigDecimal dailyVelocity;
    }
}
