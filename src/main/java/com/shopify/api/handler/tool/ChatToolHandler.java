package com.shopify.api.handler.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopify.api.model.ChatbotConfig;
import reactor.core.publisher.Mono;

/**
 * Interface for self-describing chat tool handlers.
 *
 * Each implementation is auto-discovered by ChatToolRegistry at startup
 * and made available to the chatbot without any hardcoded routing.
 *
 * To add a new tool:
 * 1. Create a @Component class implementing this interface
 * 2. Return the tool name, description, and input schema
 * 3. Implement execute() — that's it, the registry handles the rest
 */
public interface ChatToolHandler {

    /**
     * The tool name as Claude will see it (e.g., "search_products", "check_inventory")
     */
    String getToolName();

    /**
     * Human-readable description of what this tool does.
     * Claude uses this to decide when to call the tool.
     */
    String getToolDescription();

    /**
     * JSON Schema for the tool's input parameters.
     * Must be a valid JSON Schema object node with "type": "object", "properties", and "required".
     */
    JsonNode getInputSchema();

    /**
     * Execute the tool with the provided input.
     *
     * @param input JsonNode containing tool input parameters
     * @return Mono<String> containing the JSON string result for Claude
     */
    Mono<String> execute(JsonNode input);

    /**
     * Whether this tool is enabled given the current chatbot configuration.
     * Override to conditionally enable/disable tools.
     */
    default boolean isEnabled(ChatbotConfig config) {
        return true;
    }

    /**
     * Validate input before execution.
     * Override for custom validation logic.
     */
    default boolean validateInput(JsonNode input) {
        return true;
    }
}
