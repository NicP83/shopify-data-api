package com.shopify.api.dto.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopify.api.model.agent.Agent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO for agent responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentResponse {

    private Long id;
    private String name;
    private String description;
    private String modelProvider;
    private String modelName;
    private String systemPrompt;
    private BigDecimal temperature;
    private Integer maxTokens;
    private JsonNode configJson;
    private Boolean isActive;
    private Integer agentTools;
    private List<ToolSummary> tools;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Simple DTO for tool information in agent response
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ToolSummary {
        private Long id;
        private String name;
        private String description;
    }

    /**
     * Convert Agent entity to AgentResponse DTO
     */
    public static AgentResponse fromEntity(Agent agent) {
        // Count tools - handle lazy loading safely
        int toolCount = 0;
        List<ToolSummary> toolSummaries = null;

        try {
            if (agent.getAgentTools() != null) {
                toolCount = agent.getAgentTools().size();
                toolSummaries = agent.getAgentTools().stream()
                    .map(at -> ToolSummary.builder()
                        .id(at.getTool().getId())
                        .name(at.getTool().getName())
                        .description(at.getTool().getDescription())
                        .build())
                    .collect(Collectors.toList());
            }
        } catch (Exception e) {
            // Ignore lazy loading exceptions - just leave count as 0
        }

        return AgentResponse.builder()
            .id(agent.getId())
            .name(agent.getName())
            .description(agent.getDescription())
            .modelProvider(agent.getModelProvider())
            .modelName(agent.getModelName())
            .systemPrompt(agent.getSystemPrompt())
            .temperature(agent.getTemperature())
            .maxTokens(agent.getMaxTokens())
            .configJson(agent.getConfigJson())
            .isActive(agent.getIsActive())
            .agentTools(toolCount)
            .tools(toolSummaries)
            .createdAt(agent.getCreatedAt())
            .updatedAt(agent.getUpdatedAt())
            .build();
    }
}
