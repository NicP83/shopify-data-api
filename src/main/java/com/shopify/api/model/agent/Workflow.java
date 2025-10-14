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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Workflow Entity - Workflow definitions with trigger configuration and execution mode
 *
 * Represents a workflow that orchestrates multiple agents in a sequence.
 * Workflows can be triggered manually, on schedule, or by events.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 * See: docs/multi-agent/DATABASE_SCHEMA.md for schema details
 */
@Entity
@Table(name = "workflows")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Workflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "trigger_type", nullable = false, length = 50)
    private String triggerType; // MANUAL, SCHEDULED, EVENT

    @Type(JsonBinaryType.class)
    @Column(name = "trigger_config_json", columnDefinition = "jsonb")
    private JsonNode triggerConfigJson;

    @Column(name = "execution_mode", nullable = false, length = 20)
    @Builder.Default
    private String executionMode = "SYNC"; // SYNC or ASYNC

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
    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepOrder ASC")
    @Builder.Default
    private List<WorkflowStep> workflowSteps = new ArrayList<>();

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<WorkflowExecution> workflowExecutions = new HashSet<>();

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<WorkflowSchedule> workflowSchedules = new HashSet<>();

    // Helper methods for bidirectional relationships
    public void addWorkflowStep(WorkflowStep workflowStep) {
        workflowSteps.add(workflowStep);
        workflowStep.setWorkflow(this);
    }

    public void removeWorkflowStep(WorkflowStep workflowStep) {
        workflowSteps.remove(workflowStep);
        workflowStep.setWorkflow(null);
    }

    public void addWorkflowSchedule(WorkflowSchedule workflowSchedule) {
        workflowSchedules.add(workflowSchedule);
        workflowSchedule.setWorkflow(this);
    }

    public void removeWorkflowSchedule(WorkflowSchedule workflowSchedule) {
        workflowSchedules.remove(workflowSchedule);
        workflowSchedule.setWorkflow(null);
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
