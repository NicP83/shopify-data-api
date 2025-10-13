package com.shopify.api.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Client for communicating with MCP (Model Context Protocol) servers
 * Implements JSON-RPC 2.0 protocol for tool calling
 */
@Component
public class MCPClient {

    private static final Logger logger = LoggerFactory.getLogger(MCPClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public MCPClient(WebClient.Builder webClientBuilder,
                    @Value("${crs.mcp.url}") String mcpUrl,
                    @Value("${crs.mcp.enabled:true}") boolean enabled) {
        this.webClient = webClientBuilder
                .baseUrl(mcpUrl)
                .build();
        this.objectMapper = new ObjectMapper();
        this.enabled = enabled;
        logger.info("MCPClient initialized - URL: {}, Enabled: {}", mcpUrl, enabled);
    }

    /**
     * Call an MCP tool using JSON-RPC 2.0 protocol
     *
     * @param toolName Name of the tool to call
     * @param arguments Map of arguments for the tool
     * @return Mono of the tool result
     */
    public Mono<JsonNode> callTool(String toolName, Map<String, Object> arguments) {
        if (!enabled) {
            logger.warn("MCP is disabled, returning empty result");
            return Mono.just(objectMapper.createObjectNode());
        }

        try {
            // Build JSON-RPC 2.0 request
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", System.currentTimeMillis());
            request.put("method", "tools/call");

            // Build params object
            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", toolName);
            params.set("arguments", objectMapper.valueToTree(arguments));
            request.set("params", params);

            logger.debug("Calling MCP tool: {} with arguments: {}", toolName, arguments);

            return webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .map(this::extractResult)
                    .doOnSuccess(result -> logger.debug("MCP tool {} completed successfully", toolName))
                    .doOnError(error -> logger.error("MCP tool {} failed: {}", toolName, error.getMessage()));

        } catch (Exception e) {
            logger.error("Error building MCP request for tool {}: {}", toolName, e.getMessage(), e);
            return Mono.error(new RuntimeException("Failed to call MCP tool: " + toolName, e));
        }
    }

    /**
     * Extract result from JSON-RPC 2.0 response
     * Handles both success and error responses
     */
    private JsonNode extractResult(JsonNode response) {
        if (response.has("error")) {
            JsonNode error = response.get("error");
            String errorMessage = error.has("message") ? error.get("message").asText() : "Unknown MCP error";
            logger.error("MCP returned error: {}", errorMessage);
            throw new RuntimeException("MCP Error: " + errorMessage);
        }

        if (response.has("result")) {
            return response.get("result");
        }

        logger.warn("MCP response missing result field: {}", response);
        return objectMapper.createObjectNode();
    }

    /**
     * Check if MCP client is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}
