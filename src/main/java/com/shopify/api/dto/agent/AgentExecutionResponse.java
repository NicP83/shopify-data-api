package com.shopify.api.dto.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopify.api.model.agent.AgentExecution;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for agent execution responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentExecutionResponse {

    private Long id;
    private Long agentId;
    private String agentName;
    private String status;
    private JsonNode inputDataJson;
    private JsonNode outputDataJson;
    private Integer tokensUsed;
    private Integer executionTimeMs;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    /**
     * Convert AgentExecution entity to AgentExecutionResponse DTO
     */
    public static AgentExecutionResponse fromEntity(AgentExecution execution) {
        return AgentExecutionResponse.builder()
            .id(execution.getId())
            .agentId(execution.getAgent() != null ? execution.getAgent().getId() : null)
            .agentName(execution.getAgent() != null ? execution.getAgent().getName() : null)
            .status(execution.getStatus())
            .inputDataJson(execution.getInputDataJson())
            .outputDataJson(execution.getOutputDataJson())
            .tokensUsed(execution.getTokensUsed())
            .executionTimeMs(execution.getExecutionTimeMs())
            .errorMessage(execution.getErrorMessage())
            .startedAt(execution.getStartedAt())
            .completedAt(execution.getCompletedAt())
            .createdAt(execution.getCreatedAt())
            .build();
    }
}
