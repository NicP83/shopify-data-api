package com.shopify.api.service;

import com.shopify.api.model.ChatbotConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for managing chatbot configuration.
 * Loads configuration from application.yml and provides runtime updates.
 */
@Service
public class ChatbotConfigService {

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

    @Value("${chatbot.response.show-prices}")
    private boolean showPrices;

    @Value("${chatbot.response.show-skus}")
    private boolean showSkus;

    // Advanced
    @Value("${chatbot.custom-instructions}")
    private String customInstructions;

    /**
     * Get current chatbot configuration
     */
    public ChatbotConfig getConfig() {
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
                .showPrices(showPrices)
                .showSkus(showSkus)
                .customInstructions(customInstructions)
                .build();
    }

    /**
     * Update chatbot configuration (runtime only)
     */
    public void updateConfig(ChatbotConfig config) {
        if (config.getStoreName() != null) {
            this.storeName = config.getStoreName();
        }
        if (config.getStoreDescription() != null) {
            this.storeDescription = config.getStoreDescription();
        }
        if (config.getStoreCategories() != null) {
            this.storeCategories = config.getStoreCategories();
        }
        if (config.getScopeInstructions() != null) {
            this.scopeInstructions = config.getScopeInstructions();
        }
        if (config.getOutOfScopeResponse() != null) {
            this.outOfScopeResponse = config.getOutOfScopeResponse();
        }
        this.requireSearch = config.isRequireSearchBeforeRecommendation();
        this.enableProductSearch = config.isEnableProductSearch();
        this.maxSearchResults = config.getMaxSearchResults();
        if (config.getToneOfVoice() != null) {
            this.tone = config.getToneOfVoice();
        }
        this.includeCartLinks = config.isIncludeCartLinks();
        this.showPrices = config.isShowPrices();
        this.showSkus = config.isShowSkus();
        if (config.getCustomInstructions() != null) {
            this.customInstructions = config.getCustomInstructions();
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

    public boolean isShowPrices() {
        return showPrices;
    }

    public boolean isShowSkus() {
        return showSkus;
    }

    public String getCustomInstructions() {
        return customInstructions;
    }
}
