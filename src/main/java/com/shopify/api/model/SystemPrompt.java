package com.shopify.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for managing AI system prompts
 * Supports both shop-specific and global prompts with versioning
 */
@Entity
@Table(name = "system_prompts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemPrompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Shop association (null for global prompts)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id")
    private ShopifyShop shop;

    /**
     * Prompt identification
     */
    @Column(name = "prompt_name", nullable = false, length = 100)
    private String promptName;

    @Column(name = "prompt_type", nullable = false, length = 50)
    @Builder.Default
    private String promptType = "product_search";

    /**
     * Prompt content
     */
    @Column(name = "prompt_text", nullable = false, columnDefinition = "TEXT")
    private String promptText;

    @Column(name = "prompt_description", columnDefinition = "TEXT")
    private String promptDescription;

    /**
     * Versioning
     */
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Metadata
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    /**
     * Performance tracking
     */
    @Column(name = "usage_count")
    @Builder.Default
    private Integer usageCount = 0;

    @Column(name = "avg_response_time_ms")
    @Builder.Default
    private Integer avgResponseTimeMs = 0;

    @Column(name = "success_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal successRate = BigDecimal.valueOf(100.00);

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Increment usage count
     */
    public void incrementUsageCount() {
        this.usageCount = (this.usageCount == null ? 0 : this.usageCount) + 1;
    }

    /**
     * Update average response time
     * @param responseTimeMs Response time in milliseconds
     */
    public void updateAverageResponseTime(long responseTimeMs) {
        if (this.avgResponseTimeMs == null || this.usageCount == null) {
            this.avgResponseTimeMs = (int) responseTimeMs;
        } else {
            // Calculate running average
            long totalTime = (long) this.avgResponseTimeMs * (this.usageCount - 1);
            this.avgResponseTimeMs = (int) ((totalTime + responseTimeMs) / this.usageCount);
        }
    }

    /**
     * Update success rate
     * @param isSuccess Whether the interaction was successful
     */
    public void updateSuccessRate(boolean isSuccess) {
        if (this.usageCount == null || this.usageCount <= 1) {
            this.successRate = isSuccess ? BigDecimal.valueOf(100.00) : BigDecimal.ZERO;
        } else {
            // Calculate running success rate
            double currentTotal = this.successRate.doubleValue() * (this.usageCount - 1);
            double newTotal = currentTotal + (isSuccess ? 100.0 : 0.0);
            this.successRate = BigDecimal.valueOf(newTotal / this.usageCount);
        }
    }

    /**
     * Check if this is a global (non-shop-specific) prompt
     */
    public boolean isGlobal() {
        return this.shop == null;
    }

    /**
     * Prompt type constants
     */
    public static class PromptType {
        public static final String PRODUCT_SEARCH = "product_search";
        public static final String GENERAL_CHAT = "general_chat";
        public static final String ORDER_INQUIRY = "order_inquiry";
        public static final String SUPPORT = "support";
        public static final String CUSTOM = "custom";
    }
}
