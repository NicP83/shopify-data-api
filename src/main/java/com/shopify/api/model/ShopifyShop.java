package com.shopify.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Shopify Shop entity - stores OAuth tokens and shop-specific configuration
 */
@Entity
@Table(name = "shopify_shops")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopifyShop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Shop identification
    @Column(name = "shop_domain", nullable = false, unique = true)
    private String shopDomain;

    @Column(name = "shop_name")
    private String shopName;

    @Column(name = "shop_email")
    private String shopEmail;

    @Column(name = "shop_owner")
    private String shopOwner;

    // OAuth tokens
    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "scope", columnDefinition = "TEXT")
    private String scope;

    // Shop metadata
    @Column(name = "plan_name", length = 100)
    private String planName;

    @Column(name = "currency", length = 10)
    private String currency = "USD";

    @Column(name = "timezone", length = 100)
    private String timezone;

    // AI configuration
    @Column(name = "ai_enabled")
    private Boolean aiEnabled = true;

    @Column(name = "ai_model", length = 100)
    private String aiModel = "claude-sonnet-4-5-20250929";

    @Column(name = "ai_temperature", precision = 3, scale = 2)
    private BigDecimal aiTemperature = new BigDecimal("0.70");

    @Column(name = "ai_max_tokens")
    private Integer aiMaxTokens = 4096;

    @Column(name = "ai_system_prompt", columnDefinition = "TEXT")
    private String aiSystemPrompt;

    // Analytics settings
    @Column(name = "analytics_enabled")
    private Boolean analyticsEnabled = true;

    @Column(name = "track_chat_usage")
    private Boolean trackChatUsage = true;

    // Installation tracking
    @Column(name = "installed_at", nullable = false)
    private LocalDateTime installedAt;

    @Column(name = "uninstalled_at")
    private LocalDateTime uninstalledAt;

    @Column(name = "is_active")
    private Boolean isActive = true;

    // Timestamps
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (installedAt == null) {
            installedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if shop is installed and active
     */
    public boolean isInstalled() {
        return isActive && uninstalledAt == null;
    }

    /**
     * Get AI model configuration
     */
    public String getAiModel() {
        return aiModel;
    }

    /**
     * Get AI temperature configuration
     */
    public double getAiTemperatureValue() {
        return aiTemperature.doubleValue();
    }

    /**
     * Get AI max tokens configuration
     */
    public int getAiMaxTokensValue() {
        return aiMaxTokens;
    }
}
