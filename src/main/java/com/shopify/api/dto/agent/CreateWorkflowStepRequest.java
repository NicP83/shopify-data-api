package com.shopify.api.dto.agent;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating a new workflow step
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateWorkflowStepRequest {

    @NotNull(message = "Step order is required")
    @Min(value = 1, message = "Step order must be at least 1")
    private Integer stepOrder;

    @NotBlank(message = "Step type is required")
    @Pattern(regexp = "AGENT_EXECUTION|APPROVAL|CONDITION|PARALLEL",
             message = "Step type must be AGENT_EXECUTION, APPROVAL, CONDITION, or PARALLEL")
    private String stepType;

    private Long agentId;

    @Size(max = 255, message = "Step name must not exceed 255 characters")
    private String name;

    private JsonNode inputMappingJson;

    @Size(max = 100, message = "Output variable must not exceed 100 characters")
    private String outputVariable;

    private String conditionExpression;

    private Integer[] dependsOn;

    private JsonNode approvalConfigJson;

    private JsonNode retryConfigJson;

    @Min(value = 1, message = "Timeout must be at least 1 second")
    @Max(value = 3600, message = "Timeout must not exceed 3600 seconds")
    @Builder.Default
    private Integer timeoutSeconds = 300;
}
