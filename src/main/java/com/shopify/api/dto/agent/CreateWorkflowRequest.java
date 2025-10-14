package com.shopify.api.dto.agent;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating a new workflow
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateWorkflowRequest {

    @NotBlank(message = "Workflow name is required")
    @Size(max = 255, message = "Workflow name must not exceed 255 characters")
    private String name;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    @NotBlank(message = "Trigger type is required")
    @Pattern(regexp = "MANUAL|SCHEDULED|EVENT", message = "Trigger type must be MANUAL, SCHEDULED, or EVENT")
    private String triggerType;

    private JsonNode triggerConfigJson;

    @NotBlank(message = "Execution mode is required")
    @Pattern(regexp = "SYNC|ASYNC", message = "Execution mode must be SYNC or ASYNC")
    @Builder.Default
    private String executionMode = "SYNC";

    @Builder.Default
    private Boolean isActive = true;
}
