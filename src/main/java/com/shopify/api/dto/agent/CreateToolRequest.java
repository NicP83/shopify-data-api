package com.shopify.api.dto.agent;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating a new tool
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateToolRequest {

    @NotBlank(message = "Tool name is required")
    @Size(max = 100, message = "Tool name must not exceed 100 characters")
    private String name;

    @NotBlank(message = "Tool type is required")
    @Size(max = 50, message = "Tool type must not exceed 50 characters")
    private String type;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    @NotNull(message = "Input schema is required")
    private JsonNode inputSchemaJson;

    @NotBlank(message = "Handler class is required")
    @Size(max = 255, message = "Handler class must not exceed 255 characters")
    private String handlerClass;

    @Builder.Default
    private Boolean isActive = true;
}
