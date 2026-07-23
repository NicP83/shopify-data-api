package com.shopify.api.dto.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopify.api.model.agent.WorkflowExecution;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for workflow execution responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowExecutionResponse {

    private Long id;
    private Long workflowId;
    private String workflowName;
    private String status;
    private JsonNode triggerDataJson;
    private JsonNode contextDataJson;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    /**
     * Convert WorkflowExecution entity to DTO.
     * The workflow association must be fetch-joined by the caller.
     */
    public static WorkflowExecutionResponse fromEntity(WorkflowExecution execution) {
        return WorkflowExecutionResponse.builder()
            .id(execution.getId())
            .workflowId(execution.getWorkflow() != null ? execution.getWorkflow().getId() : null)
            .workflowName(execution.getWorkflow() != null ? execution.getWorkflow().getName() : null)
            .status(execution.getStatus())
            .triggerDataJson(execution.getTriggerDataJson())
            .contextDataJson(execution.getContextDataJson())
            .errorMessage(execution.getErrorMessage())
            .startedAt(execution.getStartedAt())
            .completedAt(execution.getCompletedAt())
            .createdAt(execution.getCreatedAt())
            .build();
    }
}
