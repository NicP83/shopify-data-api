package com.shopify.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request model for SEO Agent chat interactions
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeoAgentRequest {

    @JsonProperty("message")
    private String message;

    @JsonProperty("conversationHistory")
    private List<ChatMessage> conversationHistory;

    @JsonProperty("config")
    private SeoAgentConfig config;

    /**
     * SEO Agent configuration (tools, agents, LLM settings)
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SeoAgentConfig {

        @JsonProperty("selectedTools")
        private List<Long> selectedTools;

        @JsonProperty("selectedAgents")
        private List<Long> selectedAgents;

        @JsonProperty("llmConfig")
        private LlmConfig llmConfig;

        @JsonProperty("orchestrationPrompt")
        private String orchestrationPrompt;
    }

    /**
     * LLM configuration
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LlmConfig {

        @JsonProperty("model")
        @Builder.Default
        private String model = "claude-3-5-sonnet-20241022";

        @JsonProperty("temperature")
        @Builder.Default
        private Double temperature = 0.7;

        @JsonProperty("maxTokens")
        @Builder.Default
        private Integer maxTokens = 4096;

        @JsonProperty("topP")
        @Builder.Default
        private Double topP = 1.0;
    }
}
