package com.shopify.api.model.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import io.hypersistence.utils.hibernate.type.array.IntArrayType;
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
 * WorkflowStep Entity - Ordered steps within workflows with conditionals and dependencies
 *
 * Represents a single step in a workflow, which can execute an agent,
 * require approval, or perform other actions.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 * See: docs/multi-agent/DATABASE_SCHEMA.md for schema details
 */
@Entity
@Table(name = "workflow_steps")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "step_type", nullable = false, length = 50)
    private String stepType; // AGENT_EXECUTION, APPROVAL, CONDITION, etc.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @Column(name = "name", length = 255)
    private String name;

    @Type(JsonBinaryType.class)
    @Column(name = "input_mapping_json", columnDefinition = "jsonb")
    private JsonNode inputMappingJson;

    @Column(name = "output_variable", length = 100)
    private String outputVariable;

    @Column(name = "condition_expression", columnDefinition = "TEXT")
    private String conditionExpression;

    @Type(IntArrayType.class)
    @Column(name = "depends_on", columnDefinition = "integer[]")
    private Integer[] dependsOn;

    @Type(JsonBinaryType.class)
    @Column(name = "approval_config_json", columnDefinition = "jsonb")
    private JsonNode approvalConfigJson;

    @Type(JsonBinaryType.class)
    @Column(name = "retry_config_json", columnDefinition = "jsonb")
    private JsonNode retryConfigJson;

    @Column(name = "timeout_seconds")
    @Builder.Default
    private Integer timeoutSeconds = 300;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Relationships
    @OneToMany(mappedBy = "workflowStep", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<AgentExecution> agentExecutions = new HashSet<>();

    @OneToMany(mappedBy = "workflowStep", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ApprovalRequest> approvalRequests = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
