package com.shopify.api.model.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Agent Entity - Dynamic agent definitions with configurable LLMs and prompts
 *
 * Represents an AI agent in the multi-agent orchestration system.
 * Agents are completely dynamic and defined via database records (zero hardcoding).
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 * See: docs/multi-agent/DATABASE_SCHEMA.md for schema details
 */
@Entity
@Table(name = "agents")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "model_provider", nullable = false, length = 50)
    private String modelProvider; // e.g., "anthropic", "openai", "google"

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName; // e.g., "claude-3-5-sonnet-20241022", "gpt-4", "gemini-pro"

    @Column(name = "system_prompt", nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "temperature", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal temperature = new BigDecimal("0.70");

    @Column(name = "max_tokens", nullable = false)
    @Builder.Default
    private Integer maxTokens = 1024;

    @Type(JsonBinaryType.class)
    @Column(name = "config_json", columnDefinition = "jsonb")
    private JsonNode configJson;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Relationships
    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<AgentTool> agentTools = new HashSet<>();

    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<WorkflowStep> workflowSteps = new HashSet<>();

    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<AgentExecution> agentExecutions = new HashSet<>();

    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<KnowledgeBase> knowledgeBases = new HashSet<>();

    // Helper methods for bidirectional relationships
    public void addAgentTool(AgentTool agentTool) {
        agentTools.add(agentTool);
        agentTool.setAgent(this);
    }

    public void removeAgentTool(AgentTool agentTool) {
        agentTools.remove(agentTool);
        agentTool.setAgent(null);
    }

    public void addKnowledgeBase(KnowledgeBase knowledgeBase) {
        knowledgeBases.add(knowledgeBase);
        knowledgeBase.setAgent(this);
    }

    public void removeKnowledgeBase(KnowledgeBase knowledgeBase) {
        knowledgeBases.remove(knowledgeBase);
        knowledgeBase.setAgent(null);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
