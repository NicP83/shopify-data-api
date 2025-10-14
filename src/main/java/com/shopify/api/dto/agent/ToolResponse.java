package com.shopify.api.dto.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopify.api.model.agent.Tool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for tool responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolResponse {

    private Long id;
    private String name;
    private String type;
    private String description;
    private JsonNode inputSchemaJson;
    private String handlerClass;
    private Boolean isActive;
    private LocalDateTime createdAt;

    /**
     * Convert Tool entity to ToolResponse DTO
     */
    public static ToolResponse fromEntity(Tool tool) {
        return ToolResponse.builder()
            .id(tool.getId())
            .name(tool.getName())
            .type(tool.getType())
            .description(tool.getDescription())
            .inputSchemaJson(tool.getInputSchemaJson())
            .handlerClass(tool.getHandlerClass())
            .isActive(tool.getIsActive())
            .createdAt(tool.getCreatedAt())
            .build();
    }
}
