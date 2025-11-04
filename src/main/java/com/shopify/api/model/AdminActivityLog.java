package com.shopify.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Entity representing admin activity log entries for audit trail.
 * Tracks all admin actions in the Shopify admin UI.
 *
 * @see <a href="/src/main/resources/db/migration/V010__create_admin_logging_tables.sql">V010 Migration</a>
 */
@Entity
@Table(name = "admin_activity_log", indexes = {
    @Index(name = "idx_admin_activity_shop", columnList = "shop_id"),
    @Index(name = "idx_admin_activity_user", columnList = "user_email"),
    @Index(name = "idx_admin_activity_created", columnList = "created_at"),
    @Index(name = "idx_admin_activity_action", columnList = "action_category, action_type"),
    @Index(name = "idx_admin_activity_resource", columnList = "resource_type, resource_id"),
    @Index(name = "idx_admin_activity_success", columnList = "success"),
    @Index(name = "idx_admin_activity_shop_date", columnList = "shop_id, created_at")
})
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class AdminActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== WHO: User Information =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id")
    private ShopifyShop shop;

    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    @Column(name = "user_ip", length = 45)
    private String userIp;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    // ===== WHAT: Action Information =====

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;  // CREATE, UPDATE, DELETE, TEST, ROLLBACK, VIEW

    @Column(name = "action_category", nullable = false, length = 50)
    private String actionCategory;  // CONFIG, PROMPT, TESTING, ANALYTICS, SYSTEM

    @Column(name = "resource_type", nullable = false, length = 100)
    private String resourceType;  // system_prompt, shop_config, ai_model, etc.

    @Column(name = "resource_id", length = 255)
    private String resourceId;

    // ===== CHANGES: Before/After State =====

    @Type(type = "jsonb")
    @Column(name = "previous_value", columnDefinition = "jsonb")
    private Map<String, Object> previousValue;

    @Type(type = "jsonb")
    @Column(name = "new_value", columnDefinition = "jsonb")
    private Map<String, Object> newValue;

    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;

    // ===== WHEN: Timing Information =====

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    // ===== RESULT: Success/Failure =====

    @Column(name = "success", nullable = false)
    private Boolean success = true;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // ===== CONTEXT: Additional Metadata =====

    @Type(type = "jsonb")
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    // ===== GDPR: Privacy Compliance =====

    @Column(name = "anonymized", nullable = false)
    private Boolean anonymized = false;

    @Column(name = "anonymized_at")
    private ZonedDateTime anonymizedAt;

    // ===== Constructors =====

    public AdminActivityLog() {
    }

    /**
     * Create a new activity log entry
     */
    public AdminActivityLog(
            ShopifyShop shop,
            String userEmail,
            String actionType,
            String actionCategory,
            String resourceType,
            String resourceId) {
        this.shop = shop;
        this.userEmail = userEmail;
        this.actionType = actionType;
        this.actionCategory = actionCategory;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.success = true;
        this.anonymized = false;
    }

    // ===== Helper Methods =====

    /**
     * Mark this action as failed with error message
     */
    public void markFailed(String errorMessage) {
        this.success = false;
        this.errorMessage = errorMessage;
    }

    /**
     * Set the duration of this action in milliseconds
     */
    public void setDuration(long startTimeMs) {
        this.durationMs = (int) (System.currentTimeMillis() - startTimeMs);
    }

    /**
     * Anonymize user data for GDPR compliance
     */
    public void anonymize() {
        this.userEmail = "anonymized@example.com";
        this.userIp = null;
        this.userAgent = null;
        this.anonymized = true;
        this.anonymizedAt = ZonedDateTime.now();
    }

    /**
     * Check if this is a configuration change
     */
    @JsonProperty("isConfigChange")
    public boolean isConfigChange() {
        return "CONFIG".equals(this.actionCategory);
    }

    /**
     * Check if this is a prompt change
     */
    @JsonProperty("isPromptChange")
    public boolean isPromptChange() {
        return "PROMPT".equals(this.actionCategory);
    }

    // ===== Action Type Constants =====

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_TEST = "TEST";
    public static final String ACTION_ROLLBACK = "ROLLBACK";
    public static final String ACTION_VIEW = "VIEW";

    // ===== Action Category Constants =====

    public static final String CATEGORY_CONFIG = "CONFIG";
    public static final String CATEGORY_PROMPT = "PROMPT";
    public static final String CATEGORY_TESTING = "TESTING";
    public static final String CATEGORY_ANALYTICS = "ANALYTICS";
    public static final String CATEGORY_SYSTEM = "SYSTEM";

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

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getUserIp() {
        return userIp;
    }

    public void setUserIp(String userIp) {
        this.userIp = userIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getActionCategory() {
        return actionCategory;
    }

    public void setActionCategory(String actionCategory) {
        this.actionCategory = actionCategory;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public Map<String, Object> getPreviousValue() {
        return previousValue;
    }

    public void setPreviousValue(Map<String, Object> previousValue) {
        this.previousValue = previousValue;
    }

    public Map<String, Object> getNewValue() {
        return newValue;
    }

    public void setNewValue(Map<String, Object> newValue) {
        this.newValue = newValue;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Boolean getAnonymized() {
        return anonymized;
    }

    public void setAnonymized(Boolean anonymized) {
        this.anonymized = anonymized;
    }

    public ZonedDateTime getAnonymizedAt() {
        return anonymizedAt;
    }

    public void setAnonymizedAt(ZonedDateTime anonymizedAt) {
        this.anonymizedAt = anonymizedAt;
    }

    @Override
    public String toString() {
        return "AdminActivityLog{" +
                "id=" + id +
                ", userEmail='" + userEmail + '\'' +
                ", actionCategory='" + actionCategory + '\'' +
                ", actionType='" + actionType + '\'' +
                ", resourceType='" + resourceType + '\'' +
                ", resourceId='" + resourceId + '\'' +
                ", success=" + success +
                ", createdAt=" + createdAt +
                '}';
    }
}
