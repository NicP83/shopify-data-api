package com.shopify.api.dto.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopify.api.model.agent.Agent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Convert Agent entity to AgentResponse DTO
     */
    public static AgentResponse fromEntity(Agent agent) {
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
            .createdAt(agent.getCreatedAt())
            .updatedAt(agent.getUpdatedAt())
            .build();
    }
}
