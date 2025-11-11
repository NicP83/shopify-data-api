package com.shopify.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for tracking chat analytics and performance metrics
 */
@Entity
@Table(name = "chat_analytics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Shop association
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private ShopifyShop shop;

    /**
     * Session tracking
     */
    @Column(name = "session_id", length = 255)
    private String sessionId;

    @Column(name = "user_identifier", length = 255)
    private String userIdentifier;

    /**
     * Message details
     */
    @Column(name = "user_message", nullable = false, columnDefinition = "TEXT")
    private String userMessage;

    @Column(name = "assistant_response", columnDefinition = "TEXT")
    private String assistantResponse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prompt_id")
    private SystemPrompt prompt;

    /**
     * AI configuration used
     */
    @Column(name = "model_used", length = 100)
    private String modelUsed;

    @Column(name = "temperature_used", precision = 3, scale = 2)
    private BigDecimal temperatureUsed;

    @Column(name = "max_tokens_used")
    private Integer maxTokensUsed;

    /**
     * Performance metrics
     */
    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "api_cost_usd", precision = 10, scale = 4)
    private BigDecimal apiCostUsd;

    /**
     * Tool usage tracking
     * Note: tools_called column exists in DB but not mapped here due to PostgreSQL array type complexity
     * Tool count is tracked via tool_results_count instead
     */
    @Column(name = "tool_results_count")
    @Builder.Default
    private Integer toolResultsCount = 0;

    /**
     * Quality metrics
     */
    @Column(name = "was_successful")
    @Builder.Default
    private Boolean wasSuccessful = true;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "user_feedback", length = 50)
    private String userFeedback; // 'positive', 'negative', 'neutral'

    /**
     * Timestamps
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Additional metadata
     */
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "referer_url", columnDefinition = "TEXT")
    private String refererUrl;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Calculate estimated API cost based on tokens and model
     * Anthropic pricing as of 2025:
     * - Claude Sonnet 4.5: $3 per MTok input, $15 per MTok output
     * - Claude Sonnet 3.5: $3 per MTok input, $15 per MTok output
     */
    public void calculateApiCost(int inputTokens, int outputTokens) {
        if (modelUsed == null) {
            this.apiCostUsd = BigDecimal.ZERO;
            return;
        }

        double inputCostPerMTok = 3.0;
        double outputCostPerMTok = 15.0;

        // Adjust pricing based on model (if needed for different models)
        if (modelUsed.contains("haiku")) {
            inputCostPerMTok = 0.25;
            outputCostPerMTok = 1.25;
        } else if (modelUsed.contains("opus")) {
            inputCostPerMTok = 15.0;
            outputCostPerMTok = 75.0;
        }

        double inputCost = (inputTokens / 1_000_000.0) * inputCostPerMTok;
        double outputCost = (outputTokens / 1_000_000.0) * outputCostPerMTok;

        this.apiCostUsd = BigDecimal.valueOf(inputCost + outputCost);
    }

    /**
     * Feedback constants
     */
    public static class Feedback {
        public static final String POSITIVE = "positive";
        public static final String NEGATIVE = "negative";
        public static final String NEUTRAL = "neutral";
    }
}

