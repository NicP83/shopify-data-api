package com.shopify.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * API key entity. Only the SHA-256 hash of the raw key is persisted; the raw
 * value is shown once at creation time. See V021 migration.
 */
@Entity
@Table(name = "api_keys")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** First few chars of the raw key, for display/identification only. */
    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, unique = true, length = 128)
    private String keyHash;

    /** Comma-separated grants, e.g. "agents:run,workflows:run,admin". */
    @Column(nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String scopes = "";

    @Column(name = "rate_limit_per_min", nullable = false)
    @Builder.Default
    private Integer rateLimitPerMin = 60;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** CSV of allowed Referer host prefixes for embed keys; null = unrestricted. */
    @Column(name = "referer_allowlist", columnDefinition = "TEXT")
    private String refererAllowlist;

    /** For embed keys bound to a single agent. */
    @Column(name = "bound_agent_id")
    private Long boundAgentId;

    /** For embed keys bound to a single workflow. */
    @Column(name = "bound_workflow_id")
    private Long boundWorkflowId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    public List<String> getScopeList() {
        if (scopes == null || scopes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(scopes.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    public boolean hasScope(String scope) {
        return getScopeList().contains(scope);
    }

    public List<String> getRefererAllowlistEntries() {
        if (refererAllowlist == null || refererAllowlist.isBlank()) {
            return List.of();
        }
        return Arrays.stream(refererAllowlist.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }
}
