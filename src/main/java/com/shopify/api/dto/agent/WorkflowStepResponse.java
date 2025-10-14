package com.shopify.api.dto.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopify.api.model.agent.WorkflowStep;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for workflow step responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowStepResponse {

    private Long id;
    private Long workflowId;
    private Integer stepOrder;
    private String stepType;
    private Long agentId;
    private String agentName;
    private String name;
    private JsonNode inputMappingJson;
    private String outputVariable;
    private String conditionExpression;
    private Integer[] dependsOn;
    private JsonNode approvalConfigJson;
    private JsonNode retryConfigJson;
    private Integer timeoutSeconds;
    private LocalDateTime createdAt;

    /**
     * Convert WorkflowStep entity to WorkflowStepResponse DTO
     */
    public static WorkflowStepResponse fromEntity(WorkflowStep step) {
        return WorkflowStepResponse.builder()
            .id(step.getId())
            .workflowId(step.getWorkflow() != null ? step.getWorkflow().getId() : null)
            .stepOrder(step.getStepOrder())
            .stepType(step.getStepType())
            .agentId(step.getAgent() != null ? step.getAgent().getId() : null)
            .agentName(step.getAgent() != null ? step.getAgent().getName() : null)
            .name(step.getName())
            .inputMappingJson(step.getInputMappingJson())
            .outputVariable(step.getOutputVariable())
            .conditionExpression(step.getConditionExpression())
            .dependsOn(step.getDependsOn())
            .approvalConfigJson(step.getApprovalConfigJson())
            .retryConfigJson(step.getRetryConfigJson())
            .timeoutSeconds(step.getTimeoutSeconds())
            .createdAt(step.getCreatedAt())
            .build();
    }
}
