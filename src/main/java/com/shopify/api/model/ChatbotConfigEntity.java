package com.shopify.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JPA Entity for persistent chatbot configuration
 * Supports shop-specific and global configurations
 */
@Entity
@Table(name = "chatbot_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatbotConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Shop association (NULL for global config)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id")
    private ShopifyShop shop;

    // Store Identity
    @Column(name = "store_name")
    private String storeName;

    @Column(name = "store_description", columnDefinition = "TEXT")
    private String storeDescription;

    @Column(name = "store_categories", length = 500)
    private String storeCategories;

    // Behavior Rules
    @Column(name = "scope_instructions", columnDefinition = "TEXT")
    private String scopeInstructions;

    @Column(name = "out_of_scope_response", columnDefinition = "TEXT")
    private String outOfScopeResponse;

    @Column(name = "require_search_before_recommendation")
    private Boolean requireSearchBeforeRecommendation = true;

    // Tool Configuration
    @Column(name = "enable_product_search")
    private Boolean enableProductSearch = true;

    @Column(name = "max_search_results")
    private Integer maxSearchResults = 5;

    // Response Style
    @Column(name = "tone_of_voice", length = 100)
    private String toneOfVoice = "friendly";

    @Column(name = "include_cart_links")
    private Boolean includeCartLinks = true;

    @Column(name = "include_product_links")
    private Boolean includeProductLinks = true;

    @Column(name = "show_prices")
    private Boolean showPrices = true;

    @Column(name = "show_skus")
    private Boolean showSkus = false;

    // Advanced
    @Column(name = "custom_instructions", columnDefinition = "TEXT")
    private String customInstructions;

    // AI Model Settings
    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "temperature", precision = 3, scale = 2)
    private BigDecimal temperature;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    // Agent Integration (stored as comma-separated string)
    @Column(name = "linked_agent_ids", columnDefinition = "TEXT")
    private String linkedAgentIdsString;

    // Configuration metadata
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "version")
    private Integer version = 1;

    // Audit tracking
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (version == null) {
            version = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (version != null) {
            version = version + 1;
        }
    }

    /**
     * Convert comma-separated string to List of agent IDs
     */
    public List<Long> getLinkedAgentIds() {
        if (linkedAgentIdsString == null || linkedAgentIdsString.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return Arrays.stream(linkedAgentIdsString.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Convert List of agent IDs to comma-separated string
     */
    public void setLinkedAgentIds(List<Long> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            this.linkedAgentIdsString = null;
        } else {
            this.linkedAgentIdsString = agentIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
        }
    }

    /**
     * Convert entity to ChatbotConfig DTO
     */
    public ChatbotConfig toConfig() {
        ChatbotConfig config = new ChatbotConfig();
        config.setStoreName(storeName);
        config.setStoreDescription(storeDescription);
        config.setStoreCategories(storeCategories);
        config.setScopeInstructions(scopeInstructions);
        config.setOutOfScopeResponse(outOfScopeResponse);
        config.setRequireSearchBeforeRecommendation(requireSearchBeforeRecommendation != null ? requireSearchBeforeRecommendation : true);
        config.setEnableProductSearch(enableProductSearch != null ? enableProductSearch : true);
        config.setMaxSearchResults(maxSearchResults != null ? maxSearchResults : 5);
        config.setToneOfVoice(toneOfVoice);
        config.setIncludeCartLinks(includeCartLinks != null ? includeCartLinks : true);
        config.setIncludeProductLinks(includeProductLinks != null ? includeProductLinks : true);
        config.setShowPrices(showPrices != null ? showPrices : true);
        config.setShowSkus(showSkus != null ? showSkus : false);
        config.setCustomInstructions(customInstructions);
        config.setModelName(modelName);
        config.setTemperature(temperature != null ? temperature.doubleValue() : null);
        config.setMaxTokens(maxTokens);
        config.setLinkedAgentIds(getLinkedAgentIds());
        return config;
    }

    /**
     * Create entity from ChatbotConfig DTO
     */
    public static ChatbotConfigEntity fromConfig(ChatbotConfig config) {
        ChatbotConfigEntity entity = new ChatbotConfigEntity();
        entity.setStoreName(config.getStoreName());
        entity.setStoreDescription(config.getStoreDescription());
        entity.setStoreCategories(config.getStoreCategories());
        entity.setScopeInstructions(config.getScopeInstructions());
        entity.setOutOfScopeResponse(config.getOutOfScopeResponse());
        entity.setRequireSearchBeforeRecommendation(config.isRequireSearchBeforeRecommendation());
        entity.setEnableProductSearch(config.isEnableProductSearch());
        entity.setMaxSearchResults(config.getMaxSearchResults());
        entity.setToneOfVoice(config.getToneOfVoice());
        entity.setIncludeCartLinks(config.isIncludeCartLinks());
        entity.setIncludeProductLinks(config.isIncludeProductLinks());
        entity.setShowPrices(config.isShowPrices());
        entity.setShowSkus(config.isShowSkus());
        entity.setCustomInstructions(config.getCustomInstructions());
        entity.setModelName(config.getModelName());
        entity.setTemperature(config.getTemperature() != null ? BigDecimal.valueOf(config.getTemperature()) : null);
        entity.setMaxTokens(config.getMaxTokens());
        entity.setLinkedAgentIds(config.getLinkedAgentIds());
        return entity;
    }

    /**
     * Check if this is a global (non-shop-specific) config
     */
    public boolean isGlobalConfig() {
        return shop == null;
    }
}
