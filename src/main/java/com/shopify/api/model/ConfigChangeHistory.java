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
 * Entity representing configuration change history for versioning and rollback.
 * Stores complete configuration snapshots for each change.
 *
 * @see <a href="/src/main/resources/db/migration/V010__create_admin_logging_tables.sql">V010 Migration</a>
 */
@Entity
@Table(name = "config_change_history", indexes = {
    @Index(name = "idx_config_history_shop", columnList = "shop_id"),
    @Index(name = "idx_config_history_active", columnList = "shop_id, is_active"),
    @Index(name = "idx_config_history_type", columnList = "config_type"),
    @Index(name = "idx_config_history_created", columnList = "created_at"),
    @Index(name = "idx_config_history_version", columnList = "shop_id, config_type, version_number")
})
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class ConfigChangeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== What Was Changed =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private ShopifyShop shop;

    @Column(name = "config_type", nullable = false, length = 50)
    private String configType;  // AI_MODEL, BEHAVIOR, PROMPT, ANALYTICS, FULL_CONFIG

    // ===== Configuration Snapshot =====

    @Type(type = "jsonb")
    @Column(name = "config_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> configSnapshot;

    // ===== Change Metadata =====

    @Column(name = "change_type", nullable = false, length = 20)
    private String changeType;  // MANUAL, AUTOMATIC, ROLLBACK, MIGRATION

    @Column(name = "changed_by", nullable = false, length = 255)
    private String changedBy;

    @Column(name = "change_reason", columnDefinition = "TEXT")
    private String changeReason;

    // ===== Performance Tracking =====

    @Type(type = "jsonb")
    @Column(name = "performance_before", columnDefinition = "jsonb")
    private Map<String, Object> performanceBefore;

    @Type(type = "jsonb")
    @Column(name = "performance_after", columnDefinition = "jsonb")
    private Map<String, Object> performanceAfter;

    // ===== Version Control =====

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    // ===== Timestamps =====

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "activated_at")
    private ZonedDateTime activatedAt;

    @Column(name = "deactivated_at")
    private ZonedDateTime deactivatedAt;

    // ===== Rollback Support =====

    @Column(name = "can_rollback", nullable = false)
    private Boolean canRollback = true;

    @Column(name = "rollback_notes", columnDefinition = "TEXT")
    private String rollbackNotes;

    // ===== Reference to Activity Log =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_log_id")
    private AdminActivityLog activityLog;

    // ===== Constructors =====

    public ConfigChangeHistory() {
    }

    /**
     * Create a new configuration change history entry
     */
    public ConfigChangeHistory(
            ShopifyShop shop,
            String configType,
            Map<String, Object> configSnapshot,
            String changeType,
            String changedBy,
            Integer versionNumber) {
        this.shop = shop;
        this.configType = configType;
        this.configSnapshot = configSnapshot;
        this.changeType = changeType;
        this.changedBy = changedBy;
        this.versionNumber = versionNumber;
        this.isActive = false;
        this.canRollback = true;
    }

    // ===== Helper Methods =====

    /**
     * Activate this configuration version
     */
    public void activate() {
        this.isActive = true;
        this.activatedAt = ZonedDateTime.now();
        this.deactivatedAt = null;
    }

    /**
     * Deactivate this configuration version
     */
    public void deactivate() {
        this.isActive = false;
        this.deactivatedAt = ZonedDateTime.now();
    }

    /**
     * Check if this version can be rolled back to
     */
    @JsonProperty("canRollbackTo")
    public boolean canRollbackTo() {
        return this.canRollback && !this.isActive;
    }

    /**
     * Calculate how long this version was active
     */
    @JsonProperty("activeDurationMinutes")
    public Long getActiveDurationMinutes() {
        if (activatedAt == null) {
            return null;
        }
        ZonedDateTime endTime = deactivatedAt != null ? deactivatedAt : ZonedDateTime.now();
        return java.time.Duration.between(activatedAt, endTime).toMinutes();
    }

    /**
     * Calculate performance improvement percentage
     */
    @JsonProperty("performanceImprovement")
    public Double getPerformanceImprovement() {
        if (performanceBefore == null || performanceAfter == null) {
            return null;
        }
        // Example: Compare success_rate
        Object beforeRate = performanceBefore.get("success_rate");
        Object afterRate = performanceAfter.get("success_rate");
        if (beforeRate instanceof Number && afterRate instanceof Number) {
            double before = ((Number) beforeRate).doubleValue();
            double after = ((Number) afterRate).doubleValue();
            return ((after - before) / before) * 100;
        }
        return null;
    }

    // ===== Config Type Constants =====

    public static final String TYPE_AI_MODEL = "AI_MODEL";
    public static final String TYPE_BEHAVIOR = "BEHAVIOR";
    public static final String TYPE_PROMPT = "PROMPT";
    public static final String TYPE_ANALYTICS = "ANALYTICS";
    public static final String TYPE_FULL_CONFIG = "FULL_CONFIG";

    // ===== Change Type Constants =====

    public static final String CHANGE_MANUAL = "MANUAL";
    public static final String CHANGE_AUTOMATIC = "AUTOMATIC";
    public static final String CHANGE_ROLLBACK = "ROLLBACK";
    public static final String CHANGE_MIGRATION = "MIGRATION";

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

    public String getConfigType() {
        return configType;
    }

    public void setConfigType(String configType) {
        this.configType = configType;
    }

    public Map<String, Object> getConfigSnapshot() {
        return configSnapshot;
    }

    public void setConfigSnapshot(Map<String, Object> configSnapshot) {
        this.configSnapshot = configSnapshot;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    public Map<String, Object> getPerformanceBefore() {
        return performanceBefore;
    }

    public void setPerformanceBefore(Map<String, Object> performanceBefore) {
        this.performanceBefore = performanceBefore;
    }

    public Map<String, Object> getPerformanceAfter() {
        return performanceAfter;
    }

    public void setPerformanceAfter(Map<String, Object> performanceAfter) {
        this.performanceAfter = performanceAfter;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(ZonedDateTime activatedAt) {
        this.activatedAt = activatedAt;
    }

    public ZonedDateTime getDeactivatedAt() {
        return deactivatedAt;
    }

    public void setDeactivatedAt(ZonedDateTime deactivatedAt) {
        this.deactivatedAt = deactivatedAt;
    }

    public Boolean getCanRollback() {
        return canRollback;
    }

    public void setCanRollback(Boolean canRollback) {
        this.canRollback = canRollback;
    }

    public String getRollbackNotes() {
        return rollbackNotes;
    }

    public void setRollbackNotes(String rollbackNotes) {
        this.rollbackNotes = rollbackNotes;
    }

    public AdminActivityLog getActivityLog() {
        return activityLog;
    }

    public void setActivityLog(AdminActivityLog activityLog) {
        this.activityLog = activityLog;
    }

    @Override
    public String toString() {
        return "ConfigChangeHistory{" +
                "id=" + id +
                ", configType='" + configType + '\'' +
                ", versionNumber=" + versionNumber +
                ", isActive=" + isActive +
                ", changeType='" + changeType + '\'' +
                ", changedBy='" + changedBy + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
