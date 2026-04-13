package com.shopify.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.handler.tool.ChatToolHandler;
import com.shopify.api.model.ChatbotConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dynamic tool registry that auto-discovers all ChatToolHandler beans.
 *
 * Replaces the hardcoded tool routing and definition building in ChatAgentService.
 * New tools are automatically available just by creating a @Component implementing ChatToolHandler.
 */
@Service
@Slf4j
public class ChatToolRegistry {

    private final Map<String, ChatToolHandler> handlers = new LinkedHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public ChatToolRegistry(List<ChatToolHandler> toolHandlers) {
        for (ChatToolHandler handler : toolHandlers) {
            String name = handler.getToolName();
            if (handlers.containsKey(name)) {
                log.warn("Duplicate chat tool handler for '{}': {} vs {}",
                    name, handlers.get(name).getClass().getSimpleName(), handler.getClass().getSimpleName());
            }
            handlers.put(name, handler);
        }
    }

    @PostConstruct
    public void logRegisteredTools() {
        log.info("=== Chat Tool Registry: {} tools registered ===", handlers.size());
        handlers.forEach((name, handler) ->
            log.info("  - {} ({})", name, handler.getClass().getSimpleName()));
    }

    /**
     * Execute a tool by name.
     *
     * @param toolName the tool name as called by Claude
     * @param input the tool input parameters
     * @return Mono<String> containing the JSON result
     */
    public Mono<String> executeTool(String toolName, JsonNode input) {
        ChatToolHandler handler = handlers.get(toolName);
        if (handler == null) {
            log.warn("Unknown tool requested: {}", toolName);
            return Mono.just("{\"error\": \"Unknown tool: " + toolName + "\"}");
        }

        if (!handler.validateInput(input)) {
            log.warn("Invalid input for tool {}: {}", toolName, input);
            return Mono.just("{\"error\": \"Invalid input for tool: " + toolName + "\"}");
        }

        log.info("Executing tool: {} with input: {}", toolName, input);
        return handler.execute(input)
                .doOnSuccess(result -> log.debug("Tool {} completed", toolName))
                .onErrorResume(e -> {
                    log.error("Tool {} failed: {}", toolName, e.getMessage(), e);
                    return Mono.just("{\"error\": \"Tool execution failed: " + e.getMessage() + "\"}");
                });
    }

    /**
     * Build the Claude API tools array from all registered and enabled handlers.
     *
     * @param config current chatbot configuration (used for isEnabled checks)
     * @return ArrayNode of tool definitions for the Claude API request
     */
    public ArrayNode buildToolDefinitions(ChatbotConfig config) {
        ArrayNode tools = objectMapper.createArrayNode();

        for (ChatToolHandler handler : handlers.values()) {
            if (!handler.isEnabled(config)) {
                continue;
            }

            ObjectNode tool = objectMapper.createObjectNode();
            tool.put("name", handler.getToolName());
            tool.put("description", handler.getToolDescription());
            tool.set("input_schema", handler.getInputSchema());
            tools.add(tool);
        }

        log.debug("Built {} tool definitions for Claude API", tools.size());
        return tools;
    }

    /**
     * Check if a tool exists in the registry.
     */
    public boolean hasTool(String toolName) {
        return handlers.containsKey(toolName);
    }

    /**
     * Get an unmodifiable view of all registered handlers.
     */
    public Map<String, ChatToolHandler> getHandlers() {
        return Collections.unmodifiableMap(handlers);
    }
}
