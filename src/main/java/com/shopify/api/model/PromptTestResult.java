package com.shopify.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Entity representing prompt test results from the admin UI preview functionality.
 * Tracks testing outcomes, performance metrics, and tester feedback.
 *
 * @see <a href="/src/main/resources/db/migration/V010__create_admin_logging_tables.sql">V010 Migration</a>
 */
@Entity
@Table(name = "prompt_test_results", indexes = {
    @Index(name = "idx_prompt_test_shop", columnList = "shop_id"),
    @Index(name = "idx_prompt_test_prompt", columnList = "prompt_id"),
    @Index(name = "idx_prompt_test_date", columnList = "tested_at"),
    @Index(name = "idx_prompt_test_mode", columnList = "test_mode"),
    @Index(name = "idx_prompt_test_rating", columnList = "tester_rating"),
    @Index(name = "idx_prompt_test_shop_prompt", columnList = "shop_id, prompt_id, tested_at")
})
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class PromptTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== Test Context =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private ShopifyShop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prompt_id")
    private SystemPrompt prompt;  // NULL if testing unsaved prompt

    // ===== Test Input =====

    @Column(name = "test_query", nullable = false, columnDefinition = "TEXT")
    private String testQuery;

    @Type(type = "jsonb")
    @Column(name = "test_context", columnDefinition = "jsonb")
    private Map<String, Object> testContext;  // Conversation history, filters, etc.

    // ===== Test Output =====

    @Column(name = "ai_response", nullable = false, columnDefinition = "TEXT")
    private String aiResponse;

    @Column(name = "products_returned")
    private Integer productsReturned = 0;

    @Type(type = "jsonb")
    @Column(name = "product_ids", columnDefinition = "jsonb")
    private List<Long> productIds;  // Array of product IDs returned

    // ===== Performance Metrics =====

    @Column(name = "response_time_ms", nullable = false)
    private Integer responseTimeMs;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "api_cost_usd", precision = 10, scale = 6)
    private BigDecimal apiCostUsd;

    // ===== Quality Assessment =====

    @Min(1)
    @Max(5)
    @Column(name = "tester_rating")
    private Integer testerRating;  // 1-5 stars

    @Column(name = "tester_notes", columnDefinition = "TEXT")
    private String testerNotes;

    @Column(name = "auto_quality_score", precision = 5, scale = 2)
    private BigDecimal autoQualityScore;  // 0-100 automated quality score

    // ===== Test Metadata =====

    @Column(name = "test_mode", nullable = false, length = 20)
    private String testMode;  // PREVIEW, A_B_TEST, REGRESSION, MANUAL

    @Column(name = "tested_by", nullable = false, length = 255)
    private String testedBy;

    @CreationTimestamp
    @Column(name = "tested_at", nullable = false, updatable = false)
    private ZonedDateTime testedAt;

    // ===== Model Configuration Snapshot =====

    @Type(type = "jsonb")
    @Column(name = "model_config", columnDefinition = "jsonb")
    private Map<String, Object> modelConfig;  // AI settings at test time

    // ===== Reference to Activity Log =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_log_id")
    private AdminActivityLog activityLog;

    // ===== Constructors =====

    public PromptTestResult() {
    }

    /**
     * Create a new prompt test result
     */
    public PromptTestResult(
            ShopifyShop shop,
            SystemPrompt prompt,
            String testQuery,
            String aiResponse,
            Integer responseTimeMs,
            String testMode,
            String testedBy) {
        this.shop = shop;
        this.prompt = prompt;
        this.testQuery = testQuery;
        this.aiResponse = aiResponse;
        this.responseTimeMs = responseTimeMs;
        this.testMode = testMode;
        this.testedBy = testedBy;
        this.productsReturned = 0;
    }

    // ===== Helper Methods =====

    /**
     * Check if this test passed quality threshold
     */
    @JsonProperty("isQualityPassing")
    public boolean isQualityPassing() {
        // Consider passing if:
        // 1. Tester rating >= 3 (if provided), OR
        // 2. Auto quality score >= 70 (if calculated)
        if (testerRating != null && testerRating >= 3) {
            return true;
        }
        if (autoQualityScore != null && autoQualityScore.compareTo(BigDecimal.valueOf(70)) >= 0) {
            return true;
        }
        return false;
    }

    /**
     * Check if response was fast (< 2 seconds)
     */
    @JsonProperty("isFastResponse")
    public boolean isFastResponse() {
        return responseTimeMs != null && responseTimeMs < 2000;
    }

    /**
     * Check if test returned products
     */
    @JsonProperty("hasProducts")
    public boolean hasProducts() {
        return productsReturned != null && productsReturned > 0;
    }

    /**
     * Calculate cost per product found (for efficiency analysis)
     */
    @JsonProperty("costPerProduct")
    public BigDecimal getCostPerProduct() {
        if (apiCostUsd == null || productsReturned == null || productsReturned == 0) {
            return null;
        }
        return apiCostUsd.divide(BigDecimal.valueOf(productsReturned), 6, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Get response time category for reporting
     */
    @JsonProperty("responseTimeCategory")
    public String getResponseTimeCategory() {
        if (responseTimeMs == null) return "UNKNOWN";
        if (responseTimeMs < 1000) return "FAST";
        if (responseTimeMs < 3000) return "MEDIUM";
        return "SLOW";
    }

    /**
     * Calculate auto quality score based on metrics
     * Called after test completion
     */
    public void calculateAutoQualityScore() {
        if (responseTimeMs == null) {
            this.autoQualityScore = null;
            return;
        }

        double score = 100.0;

        // Deduct points for slow response (max -30 points)
        if (responseTimeMs > 5000) {
            score -= 30;
        } else if (responseTimeMs > 3000) {
            score -= 20;
        } else if (responseTimeMs > 2000) {
            score -= 10;
        }

        // Deduct points if no products returned (when expected) (max -40 points)
        if (productsReturned == null || productsReturned == 0) {
            score -= 40;
        }

        // Bonus points for many relevant products (max +10 points)
        if (productsReturned != null && productsReturned >= 5) {
            score += 10;
        }

        // Ensure score is within 0-100 range
        score = Math.max(0, Math.min(100, score));

        this.autoQualityScore = BigDecimal.valueOf(score);
    }

    // ===== Test Mode Constants =====

    public static final String MODE_PREVIEW = "PREVIEW";
    public static final String MODE_A_B_TEST = "A_B_TEST";
    public static final String MODE_REGRESSION = "REGRESSION";
    public static final String MODE_MANUAL = "MANUAL";

    // ===== Getters and Setters =====

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ShopifyShop getShop() {
        return shop;
    }

    public void setShop(ShopifyShop shop) {
        this.shop = shop;
    }

    public SystemPrompt getPrompt() {
        return prompt;
    }

    public void setPrompt(SystemPrompt prompt) {
        this.prompt = prompt;
    }

    public String getTestQuery() {
        return testQuery;
    }

    public void setTestQuery(String testQuery) {
        this.testQuery = testQuery;
    }

    public Map<String, Object> getTestContext() {
        return testContext;
    }

    public void setTestContext(Map<String, Object> testContext) {
        this.testContext = testContext;
    }

    public String getAiResponse() {
        return aiResponse;
    }

    public void setAiResponse(String aiResponse) {
        this.aiResponse = aiResponse;
    }

    public Integer getProductsReturned() {
        return productsReturned;
    }

    public void setProductsReturned(Integer productsReturned) {
        this.productsReturned = productsReturned;
    }

    public List<Long> getProductIds() {
        return productIds;
    }

    public void setProductIds(List<Long> productIds) {
        this.productIds = productIds;
    }

    public Integer getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(Integer responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public Integer getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(Integer tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public BigDecimal getApiCostUsd() {
        return apiCostUsd;
    }

    public void setApiCostUsd(BigDecimal apiCostUsd) {
        this.apiCostUsd = apiCostUsd;
    }

    public Integer getTesterRating() {
        return testerRating;
    }

    public void setTesterRating(Integer testerRating) {
        this.testerRating = testerRating;
    }

    public String getTesterNotes() {
        return testerNotes;
    }

    public void setTesterNotes(String testerNotes) {
        this.testerNotes = testerNotes;
    }

    public BigDecimal getAutoQualityScore() {
        return autoQualityScore;
    }

    public void setAutoQualityScore(BigDecimal autoQualityScore) {
        this.autoQualityScore = autoQualityScore;
    }

    public String getTestMode() {
        return testMode;
    }

    public void setTestMode(String testMode) {
        this.testMode = testMode;
    }

    public String getTestedBy() {
        return testedBy;
    }

    public void setTestedBy(String testedBy) {
        this.testedBy = testedBy;
    }

    public ZonedDateTime getTestedAt() {
        return testedAt;
    }

    public void setTestedAt(ZonedDateTime testedAt) {
        this.testedAt = testedAt;
    }

    public Map<String, Object> getModelConfig() {
        return modelConfig;
    }

    public void setModelConfig(Map<String, Object> modelConfig) {
        this.modelConfig = modelConfig;
    }

    public AdminActivityLog getActivityLog() {
        return activityLog;
    }

    public void setActivityLog(AdminActivityLog activityLog) {
        this.activityLog = activityLog;
    }

    @Override
    public String toString() {
        return "PromptTestResult{" +
                "id=" + id +
                ", testQuery='" + testQuery + '\'' +
                ", productsReturned=" + productsReturned +
                ", responseTimeMs=" + responseTimeMs +
                ", testerRating=" + testerRating +
                ", autoQualityScore=" + autoQualityScore +
                ", testMode='" + testMode + '\'' +
                ", testedAt=" + testedAt +
                '}';
    }
}
