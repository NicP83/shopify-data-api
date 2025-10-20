package com.shopify.api.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;

/**
 * Interface for tool handlers that execute tool functionality
 *
 * Each tool in the system has a handler class that implements this interface.
 * The handler is responsible for:
 * - Validating input according to the tool's input schema
 * - Executing the tool's functionality
 * - Returning results in a format Claude can understand
 *
 * Tool handlers are loaded dynamically via reflection based on the
 * handler_class field in the tool database record.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 */
public interface ToolHandler {

    /**
     * Execute the tool with the provided input
     *
     * @param input JsonNode containing tool input parameters matching the tool's input schema
     * @return Mono<JsonNode> containing the tool execution result
     */
    Mono<JsonNode> execute(JsonNode input);

    /**
     * Validate input against the tool's expected schema
     * This is optional - default implementation returns true
     *
     * @param input JsonNode to validate
     * @return true if input is valid, false otherwise
     */
    default boolean validateInput(JsonNode input) {
        return true;
    }
}
