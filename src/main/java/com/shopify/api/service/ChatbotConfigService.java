package com.shopify.api.service;

import com.shopify.api.model.ChatbotConfig;
import com.shopify.api.model.ChatbotConfigEntity;
import com.shopify.api.repository.ChatbotConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing chatbot configuration.
 * Loads configuration from database first, falls back to application.yml defaults.
 * All changes are persisted to the database.
 */
@Service
public class ChatbotConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ChatbotConfigService.class);

    private final ChatbotConfigRepository configRepository;

    // Store Identity
    @Value("${chatbot.store.name}")
    private String storeName;

    @Value("${chatbot.store.description}")
    private String storeDescription;

    @Value("${chatbot.store.categories}")
    private String storeCategories;

    // Behavior Rules
    @Value("${chatbot.behavior.scope-instructions}")
    private String scopeInstructions;

    @Value("${chatbot.behavior.out-of-scope-response}")
    private String outOfScopeResponse;

    @Value("${chatbot.behavior.require-search}")
    private boolean requireSearch;

    // Tool Configuration
    @Value("${chatbot.tools.enable-product-search}")
    private boolean enableProductSearch;

    @Value("${chatbot.tools.max-search-results}")
    private int maxSearchResults;

    // Response Style
    @Value("${chatbot.response.tone}")
    private String tone;

    @Value("${chatbot.response.include-cart-links}")
    private boolean includeCartLinks;

    @Value("${chatbot.response.include-product-links}")
    private boolean includeProductLinks;

    @Value("${chatbot.response.show-prices}")
    private boolean showPrices;

    @Value("${chatbot.response.show-skus}")
    private boolean showSkus;

    // Advanced
    @Value("${chatbot.custom-instructions}")
    private String customInstructions;

    // AI Model Settings (optional, default to null = use system defaults)
    @Value("${chatbot.ai.model-name:#{null}}")
    private String modelName;

    @Value("${chatbot.ai.temperature:#{null}}")
    private Double temperature;

    @Value("${chatbot.ai.max-tokens:#{null}}")
    private Integer maxTokens;

    // Agent Integration (optional) - comma-separated list of agent IDs
    @Value("${chatbot.agent.linked-ids:#{null}}")
    private String linkedAgentIdsConfig;

    // Workflow Integration (optional) - comma-separated list of workflow IDs
    @Value("${chatbot.workflow.linked-ids:#{null}}")
    private String linkedWorkflowIdsConfig;

    public ChatbotConfigService(ChatbotConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    /**
     * Get current chatbot configuration
     * Loads from database first, falls back to @Value defaults
     * Cached to avoid database query on every chat request
     */
    @Cacheable(value = "chatbotConfig", key = "'global'")
    public ChatbotConfig getConfig() {
        return getConfig(null); // Use global config by default
    }

    /**
     * Get chatbot configuration for a specific shop
     * @param shopId Shop ID (null for global config)
     */
    @Cacheable(value = "chatbotConfig", key = "#shopId == null ? 'global' : #shopId")
    public ChatbotConfig getConfig(Long shopId) {
        // Try to load from database
        Optional<ChatbotConfigEntity> configEntity;

        if (shopId != null) {
            configEntity = configRepository.findByShopId(shopId);
        } else {
            configEntity = configRepository.findGlobalConfig();
        }

        // If found in database, return it
        if (configEntity.isPresent()) {
            logger.debug("Loaded config from database for shop: {}", shopId);
            return configEntity.get().toConfig();
        }

        // Otherwise, return defaults from application.yml
        logger.debug("Using default config from application.yml for shop: {}", shopId);
        return getDefaultConfig();
    }

    /**
     * Get default configuration from application.yml properties
     */
    private ChatbotConfig getDefaultConfig() {
        return ChatbotConfig.builder()
                .storeName(storeName)
                .storeDescription(storeDescription)
                .storeCategories(storeCategories)
                .scopeInstructions(scopeInstructions)
                .outOfScopeResponse(outOfScopeResponse)
                .requireSearchBeforeRecommendation(requireSearch)
                .enableProductSearch(enableProductSearch)
                .maxSearchResults(maxSearchResults)
                .toneOfVoice(tone)
                .includeCartLinks(includeCartLinks)
                .includeProductLinks(includeProductLinks)
                .showPrices(showPrices)
                .showSkus(showSkus)
                .customInstructions(customInstructions)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .linkedAgentIds(parseLinkedAgentIds(linkedAgentIdsConfig))
                .linkedWorkflowIds(parseLinkedAgentIds(linkedWorkflowIdsConfig))
                .build();
    }

    /**
     * Update chatbot configuration and persist to database
     * Evicts cache to ensure fresh config is loaded
     * @param config The updated configuration
     */
    @CacheEvict(value = "chatbotConfig", key = "'global'")
    public ChatbotConfig updateConfig(ChatbotConfig config) {
        return updateConfig(config, null, "system");
    }

    /**
     * Update chatbot configuration and persist to database
     * Evicts cache to ensure fresh config is loaded
     * @param config The updated configuration
     * @param shopId Shop ID (null for global config)
     * @param updatedBy Username of who made the update
     */
    @CacheEvict(value = "chatbotConfig", key = "#shopId == null ? 'global' : #shopId")
    public ChatbotConfig updateConfig(ChatbotConfig config, Long shopId, String updatedBy) {
        logger.info("Updating chatbot config for shop: {}", shopId);

        // Load existing config or create new one
        Optional<ChatbotConfigEntity> existingEntity;

        if (shopId != null) {
            existingEntity = configRepository.findByShopId(shopId);
        } else {
            existingEntity = configRepository.findGlobalConfig();
        }

        ChatbotConfigEntity entity;

        if (existingEntity.isPresent()) {
            // Update existing entity
            entity = existingEntity.get();
            updateEntityFromConfig(entity, config);
            entity.setUpdatedBy(updatedBy);
        } else {
            // Create new entity
            entity = ChatbotConfigEntity.fromConfig(config);
            entity.setCreatedBy(updatedBy);
            entity.setUpdatedBy(updatedBy);
            // Note: shop relationship would need to be set if shopId is provided
            // For now, we'll handle global configs only
        }

        // Save to database
        ChatbotConfigEntity savedEntity = configRepository.save(entity);
        logger.info("Config saved to database with ID: {}", savedEntity.getId());

        return savedEntity.toConfig();
    }

    /**
     * Update entity fields from config DTO
     */
    private void updateEntityFromConfig(ChatbotConfigEntity entity, ChatbotConfig config) {
        if (config.getStoreName() != null) {
            entity.setStoreName(config.getStoreName());
        }
        if (config.getStoreDescription() != null) {
            entity.setStoreDescription(config.getStoreDescription());
        }
        if (config.getStoreCategories() != null) {
            entity.setStoreCategories(config.getStoreCategories());
        }
        if (config.getScopeInstructions() != null) {
            entity.setScopeInstructions(config.getScopeInstructions());
        }
        if (config.getOutOfScopeResponse() != null) {
            entity.setOutOfScopeResponse(config.getOutOfScopeResponse());
        }
        entity.setRequireSearchBeforeRecommendation(config.isRequireSearchBeforeRecommendation());
        entity.setEnableProductSearch(config.isEnableProductSearch());
        entity.setMaxSearchResults(config.getMaxSearchResults());
        if (config.getToneOfVoice() != null) {
            entity.setToneOfVoice(config.getToneOfVoice());
        }
        entity.setIncludeCartLinks(config.isIncludeCartLinks());
        entity.setIncludeProductLinks(config.isIncludeProductLinks());
        entity.setShowPrices(config.isShowPrices());
        entity.setShowSkus(config.isShowSkus());
        if (config.getCustomInstructions() != null) {
            entity.setCustomInstructions(config.getCustomInstructions());
        }
        // Update AI model settings
        if (config.getModelName() != null) {
            entity.setModelName(config.getModelName());
        }
        if (config.getTemperature() != null) {
            entity.setTemperature(java.math.BigDecimal.valueOf(config.getTemperature()));
        }
        if (config.getMaxTokens() != null) {
            entity.setMaxTokens(config.getMaxTokens());
        }
        // Update agent integration settings
        if (config.getLinkedAgentIds() != null) {
            entity.setLinkedAgentIds(config.getLinkedAgentIds());
        }
        // Update workflow integration settings
        if (config.getLinkedWorkflowIds() != null) {
            entity.setLinkedWorkflowIds(config.getLinkedWorkflowIds());
        }
    }

    /**
     * Parse linked agent IDs from comma-separated string
     */
    private List<Long> parseLinkedAgentIds(String config) {
        if (config == null || config.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return Arrays.stream(config.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            logger.warn("Invalid linked agent IDs config: {}", config);
            return Collections.emptyList();
        }
    }

    /**
     * Reset to default configuration from application.yml
     */
    public void resetToDefaults() {
        // This would reload from application.yml
        // For now, we'll just keep current values as they're already defaults
        // In a full implementation, this would trigger a context refresh
    }

    // Getters for individual properties (used by ChatAgentService)

    public String getStoreName() {
        return storeName;
    }

    public String getStoreDescription() {
        return storeDescription;
    }

    public String getStoreCategories() {
        return storeCategories;
    }

    public String getScopeInstructions() {
        return scopeInstructions;
    }

    public String getOutOfScopeResponse() {
        return outOfScopeResponse;
    }

    public boolean isRequireSearch() {
        return requireSearch;
    }

    public boolean isEnableProductSearch() {
        return enableProductSearch;
    }

    public int getMaxSearchResults() {
        return maxSearchResults;
    }

    public String getTone() {
        return tone;
    }

    public boolean isIncludeCartLinks() {
        return includeCartLinks;
    }

    public boolean isIncludeProductLinks() {
        return includeProductLinks;
    }

    public boolean isShowPrices() {
        return showPrices;
    }

    public boolean isShowSkus() {
        return showSkus;
    }

    public String getCustomInstructions() {
        return customInstructions;
    }

    public String getModelName() {
        return modelName;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public List<Long> getLinkedAgentIds() {
        return parseLinkedAgentIds(linkedAgentIdsConfig);
    }
}
