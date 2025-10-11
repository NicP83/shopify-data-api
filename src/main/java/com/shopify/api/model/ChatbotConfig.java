package com.shopify.api.model;

/**
 * Configuration model for chatbot behavior and settings.
 * Supports full customization of store identity, conversation scope, and response style.
 */
public class ChatbotConfig {

    // ===== Store Identity =====
    private String storeName;
    private String storeDescription;
    private String storeCategories;  // e.g., "model kits, paints, tools"

    // ===== Behavior Rules =====
    private String scopeInstructions;  // What products we sell
    private String outOfScopeResponse;  // What to say when asked about other products
    private boolean requireSearchBeforeRecommendation;

    // ===== Tool Configuration =====
    private boolean enableProductSearch;
    private int maxSearchResults;

    // ===== Response Style =====
    private String toneOfVoice;  // "professional", "friendly", "enthusiastic"
    private boolean includeCartLinks;
    private boolean showPrices;
    private boolean showSkus;

    // ===== Advanced =====
    private String customInstructions;  // Additional custom prompt instructions

    // ===== Constructors =====

    public ChatbotConfig() {
        // Default constructor
    }

    // ===== Getters and Setters =====

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public String getStoreDescription() {
        return storeDescription;
    }

    public void setStoreDescription(String storeDescription) {
        this.storeDescription = storeDescription;
    }

    public String getStoreCategories() {
        return storeCategories;
    }

    public void setStoreCategories(String storeCategories) {
        this.storeCategories = storeCategories;
    }

    public String getScopeInstructions() {
        return scopeInstructions;
    }

    public void setScopeInstructions(String scopeInstructions) {
        this.scopeInstructions = scopeInstructions;
    }

    public String getOutOfScopeResponse() {
        return outOfScopeResponse;
    }

    public void setOutOfScopeResponse(String outOfScopeResponse) {
        this.outOfScopeResponse = outOfScopeResponse;
    }

    public boolean isRequireSearchBeforeRecommendation() {
        return requireSearchBeforeRecommendation;
    }

    public void setRequireSearchBeforeRecommendation(boolean requireSearchBeforeRecommendation) {
        this.requireSearchBeforeRecommendation = requireSearchBeforeRecommendation;
    }

    public boolean isEnableProductSearch() {
        return enableProductSearch;
    }

    public void setEnableProductSearch(boolean enableProductSearch) {
        this.enableProductSearch = enableProductSearch;
    }

    public int getMaxSearchResults() {
        return maxSearchResults;
    }

    public void setMaxSearchResults(int maxSearchResults) {
        this.maxSearchResults = maxSearchResults;
    }

    public String getToneOfVoice() {
        return toneOfVoice;
    }

    public void setToneOfVoice(String toneOfVoice) {
        this.toneOfVoice = toneOfVoice;
    }

    public boolean isIncludeCartLinks() {
        return includeCartLinks;
    }

    public void setIncludeCartLinks(boolean includeCartLinks) {
        this.includeCartLinks = includeCartLinks;
    }

    public boolean isShowPrices() {
        return showPrices;
    }

    public void setShowPrices(boolean showPrices) {
        this.showPrices = showPrices;
    }

    public boolean isShowSkus() {
        return showSkus;
    }

    public void setShowSkus(boolean showSkus) {
        this.showSkus = showSkus;
    }

    public String getCustomInstructions() {
        return customInstructions;
    }

    public void setCustomInstructions(String customInstructions) {
        this.customInstructions = customInstructions;
    }

    // ===== Builder Pattern (Optional but helpful) =====

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ChatbotConfig config = new ChatbotConfig();

        public Builder storeName(String storeName) {
            config.storeName = storeName;
            return this;
        }

        public Builder storeDescription(String storeDescription) {
            config.storeDescription = storeDescription;
            return this;
        }

        public Builder storeCategories(String storeCategories) {
            config.storeCategories = storeCategories;
            return this;
        }

        public Builder scopeInstructions(String scopeInstructions) {
            config.scopeInstructions = scopeInstructions;
            return this;
        }

        public Builder outOfScopeResponse(String outOfScopeResponse) {
            config.outOfScopeResponse = outOfScopeResponse;
            return this;
        }

        public Builder requireSearchBeforeRecommendation(boolean requireSearch) {
            config.requireSearchBeforeRecommendation = requireSearch;
            return this;
        }

        public Builder enableProductSearch(boolean enableSearch) {
            config.enableProductSearch = enableSearch;
            return this;
        }

        public Builder maxSearchResults(int maxResults) {
            config.maxSearchResults = maxResults;
            return this;
        }

        public Builder toneOfVoice(String tone) {
            config.toneOfVoice = tone;
            return this;
        }

        public Builder includeCartLinks(boolean includeLinks) {
            config.includeCartLinks = includeLinks;
            return this;
        }

        public Builder showPrices(boolean showPrices) {
            config.showPrices = showPrices;
            return this;
        }

        public Builder showSkus(boolean showSkus) {
            config.showSkus = showSkus;
            return this;
        }

        public Builder customInstructions(String instructions) {
            config.customInstructions = instructions;
            return this;
        }

        public ChatbotConfig build() {
            return config;
        }
    }

    @Override
    public String toString() {
        return "ChatbotConfig{" +
                "storeName='" + storeName + '\'' +
                ", storeDescription='" + storeDescription + '\'' +
                ", storeCategories='" + storeCategories + '\'' +
                ", scopeInstructions='" + scopeInstructions + '\'' +
                ", outOfScopeResponse='" + outOfScopeResponse + '\'' +
                ", requireSearchBeforeRecommendation=" + requireSearchBeforeRecommendation +
                ", enableProductSearch=" + enableProductSearch +
                ", maxSearchResults=" + maxSearchResults +
                ", toneOfVoice='" + toneOfVoice + '\'' +
                ", includeCartLinks=" + includeCartLinks +
                ", showPrices=" + showPrices +
                ", showSkus=" + showSkus +
                ", customInstructions='" + customInstructions + '\'' +
                '}';
    }
}
