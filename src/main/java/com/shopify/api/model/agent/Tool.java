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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Tool Entity - Registry of all available tools that agents can use
 *
 * Tools represent capabilities that can be dynamically assigned to agents.
 * Each tool has a handler class that implements the actual functionality.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 * See: docs/multi-agent/DATABASE_SCHEMA.md for schema details
 */
@Entity
@Table(name = "tools")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "type", nullable = false, length = 50)
    private String type; // e.g., "SHOPIFY", "DATABASE", "API", "SYSTEM"

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Type(JsonBinaryType.class)
    @Column(name = "input_schema_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode inputSchemaJson;

    @Column(name = "handler_class", nullable = false, length = 255)
    private String handlerClass; // Fully qualified class name

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Relationships
    @OneToMany(mappedBy = "tool", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<AgentTool> agentTools = new HashSet<>();

    // Helper methods for bidirectional relationships
    public void addAgentTool(AgentTool agentTool) {
        agentTools.add(agentTool);
        agentTool.setTool(this);
    }

    public void removeAgentTool(AgentTool agentTool) {
        agentTools.remove(agentTool);
        agentTool.setTool(null);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
