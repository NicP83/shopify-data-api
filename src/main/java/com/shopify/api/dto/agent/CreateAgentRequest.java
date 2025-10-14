package com.shopify.api.dto.agent;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for creating a new agent
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAgentRequest {

    @NotBlank(message = "Agent name is required")
    @Size(max = 255, message = "Agent name must not exceed 255 characters")
    private String name;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    @NotBlank(message = "Model provider is required")
    @Size(max = 50, message = "Model provider must not exceed 50 characters")
    private String modelProvider;

    @NotBlank(message = "Model name is required")
    @Size(max = 100, message = "Model name must not exceed 100 characters")
    private String modelName;

    @NotBlank(message = "System prompt is required")
    private String systemPrompt;

    @DecimalMin(value = "0.0", message = "Temperature must be at least 0.0")
    @DecimalMax(value = "2.0", message = "Temperature must not exceed 2.0")
    private BigDecimal temperature;

    @Min(value = 1, message = "Max tokens must be at least 1")
    @Max(value = 100000, message = "Max tokens must not exceed 100000")
    private Integer maxTokens;

    private JsonNode configJson;

    @Builder.Default
    private Boolean isActive = true;
}
