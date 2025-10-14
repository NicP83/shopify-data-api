package com.shopify.api.dto.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopify.api.model.agent.Workflow;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for workflow responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowResponse {

    private Long id;
    private String name;
    private String description;
    private String triggerType;
    private JsonNode triggerConfigJson;
    private String executionMode;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer stepCount;

    /**
     * Convert Workflow entity to WorkflowResponse DTO
     */
    public static WorkflowResponse fromEntity(Workflow workflow) {
        return WorkflowResponse.builder()
            .id(workflow.getId())
            .name(workflow.getName())
            .description(workflow.getDescription())
            .triggerType(workflow.getTriggerType())
            .triggerConfigJson(workflow.getTriggerConfigJson())
            .executionMode(workflow.getExecutionMode())
            .isActive(workflow.getIsActive())
            .createdAt(workflow.getCreatedAt())
            .updatedAt(workflow.getUpdatedAt())
            .stepCount(workflow.getWorkflowSteps() != null ? workflow.getWorkflowSteps().size() : 0)
            .build();
    }
}
