package com.shopify.api.model.inventory;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing an AI-generated order recommendation
 * Contains detailed analysis and actionable ordering suggestions
 */
@Entity
@Table(name = "order_recommendations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String sku;

    @Column(name = "product_title", length = 500)
    private String productTitle;

    // Stock information
    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    @Column(name = "erp_stock")
    private Integer erpStock; // Stock level from ERP system

    @Column(name = "shopify_stock")
    private Integer shopifyStock; // Stock level from Shopify (for cross-reference)

    // Recommendation details
    @Column(name = "recommended_quantity", nullable = false)
    private Integer recommendedQuantity;

    @Column(name = "reorder_point", nullable = false)
    private Integer reorderPoint;

    @Column(name = "safety_stock_level")
    private Integer safetyStockLevel;

    // Supplier information
    @Column(name = "lead_time_days", nullable = false)
    private Integer leadTimeDays;

    @Column(name = "supplier_name", length = 255)
    private String supplierName;

    @Column(name = "cost_per_unit", precision = 10, scale = 2)
    private BigDecimal costPerUnit;

    @Column(name = "total_cost", precision = 10, scale = 2)
    private BigDecimal totalCost; // recommendedQuantity × costPerUnit

    // Urgency and confidence
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UrgencyLevel urgency;

    @Column(name = "confidence_score", precision = 3, scale = 2)
    private BigDecimal confidenceScore; // 0.00 to 1.00

    // AI-generated insights
    @Column(name = "ai_reasoning", columnDefinition = "TEXT")
    private String aiReasoning; // Claude's detailed analysis

    @Column(name = "recommendations", columnDefinition = "TEXT")
    private String recommendations; // Specific action items

    // Predictions
    @Column(name = "estimated_stockout_date")
    private LocalDate estimatedStockoutDate;

    @Column(name = "days_until_stockout")
    private Integer daysUntilStockout;

    // Status tracking
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private RecommendationStatus status = RecommendationStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    // Link to sales velocity calculation
    @Column(name = "velocity_id")
    private Long velocityId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = RecommendationStatus.PENDING;
        }
        // Calculate total cost if not already set
        if (totalCost == null && costPerUnit != null && recommendedQuantity != null) {
            totalCost = costPerUnit.multiply(BigDecimal.valueOf(recommendedQuantity));
        }
    }

    /**
     * Approve this recommendation for ordering
     * @param approvedBy User or system that approved
     */
    public void approve(String approvedBy) {
        this.status = RecommendationStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
        this.approvedBy = approvedBy;
    }

    /**
     * Mark this recommendation as ordered
     */
    public void markAsOrdered() {
        this.status = RecommendationStatus.ORDERED;
    }

    /**
     * Dismiss this recommendation
     */
    public void dismiss() {
        this.status = RecommendationStatus.DISMISSED;
    }

    /**
     * Check if this recommendation is urgent (CRITICAL or HIGH)
     */
    public boolean isUrgent() {
        return urgency == UrgencyLevel.CRITICAL || urgency == UrgencyLevel.HIGH;
    }

    /**
     * Check if confidence is high (>= 0.8)
     */
    public boolean isHighConfidence() {
        return confidenceScore != null && confidenceScore.compareTo(BigDecimal.valueOf(0.8)) >= 0;
    }
}
