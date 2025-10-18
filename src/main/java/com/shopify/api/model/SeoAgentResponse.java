package com.shopify.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response model for SEO Agent chat interactions
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeoAgentResponse {

    @JsonProperty("message")
    private ChatMessage message;

    @JsonProperty("toolsUsed")
    private List<String> toolsUsed;

    @JsonProperty("agentsInvoked")
    private List<String> agentsInvoked;

    @JsonProperty("metadata")
    private ResponseMetadata metadata;

    /**
     * Response metadata
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResponseMetadata {

        @JsonProperty("tokensUsed")
        private Integer tokensUsed;

        @JsonProperty("processingTimeMs")
        private Long processingTimeMs;

        @JsonProperty("modelUsed")
        private String modelUsed;
    }
}
