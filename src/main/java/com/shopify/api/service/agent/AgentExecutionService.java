package com.shopify.api.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.client.MCPClient;
import com.shopify.api.config.ModelConfig;
import com.shopify.api.model.agent.Agent;
import com.shopify.api.model.agent.AgentExecution;
import com.shopify.api.model.agent.AgentTool;
import com.shopify.api.model.agent.Tool;
import com.shopify.api.repository.agent.AgentExecutionRepository;
import com.shopify.api.repository.agent.AgentRepository;
import com.shopify.api.repository.agent.AgentToolRepository;
import com.shopify.api.service.ModelValidationService;
import com.shopify.api.service.tool.ToolHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for executing AI agents
 *
 * This service handles the execution of database-defined agents with:
 * - Dynamic tool loading from agent_tools table
 * - Multi-turn conversations with tool use
 * - Execution logging to agent_executions table
 * - Support for multiple LLM providers (Claude, GPT, Gemini)
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentExecutionService {

    private final AgentRepository agentRepository;
    private final AgentToolRepository agentToolRepository;
    private final AgentExecutionRepository agentExecutionRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final MCPClient mcpClient;
    private final ModelValidationService modelValidationService;
    private final ToolRegistryService toolRegistryService;
    private final ApplicationContext applicationContext;

    @Value("${anthropic.api.key:}")
    private String anthropicApiKey;

    @Value("${anthropic.api.version:2023-06-01}")
    private String anthropicApiVersion;

    /** Max tool-use iterations for a single agent run. Lowered from 10 to keep "expert help" snappy. */
    @Value("${agent.max-iterations:6}")
    private int maxAgentIterations;

    /**
     * Model used when an agent is invoked via storefront chat delegation ("expert help").
     * Defaults to the fastest model so customers aren't left waiting; blank = keep the agent's own model.
     */
    @Value("${agent.delegation.model:claude-haiku-4-5-20251001}")
    private String delegationModel;

    /**
     * Execute an agent with given input and return structured result
     *
     * @param agentId The ID of the agent to execute
     * @param input   The input data for the agent (JSON)
     * @return Execution result with output, tokens used, and execution ID
     */
    @Transactional
    public Mono<AgentExecutionResult> executeAgent(Long agentId, JsonNode input) {
        return runAgent(agentId, input, null);
    }

    /**
     * Execute an agent invoked via storefront chat delegation. Runs on the fast delegation model
     * (see {@code agent.delegation.model}) so "expert help" responses come back quickly.
     */
    @Transactional
    public Mono<AgentExecutionResult> executeAgentForDelegation(Long agentId, JsonNode input) {
        return runAgent(agentId, input, delegationModel);
    }

    /**
     * Shared implementation. Called only from the @Transactional public entry points above, so the
     * synchronous agent load + execution-record save run inside the caller's transaction.
     *
     * @param modelOverride if non-blank, use this model instead of the agent's configured model
     */
    private Mono<AgentExecutionResult> runAgent(Long agentId, JsonNode input, String modelOverride) {
        log.info("Executing agent ID: {} with input", agentId);

        // Load agent from database BEFORE reactive chain (within transaction context)
        // This ensures agentTools are eagerly fetched and available
        Agent agent = agentRepository.findByIdWithTools(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found with ID: " + agentId));

        // Validate agent is active
        if (!agent.getIsActive()) {
            return Mono.error(new IllegalStateException("Agent is not active: " + agent.getName()));
        }

        // Create execution record
        AgentExecution execution = AgentExecution.builder()
                .agent(agent)
                .status("RUNNING")
                .inputDataJson(input)
                .startedAt(LocalDateTime.now())
                .build();

        AgentExecution savedExecution = agentExecutionRepository.save(execution);
        log.info("Created execution record: {}", savedExecution.getId());

        // Execute based on provider and return reactive result
        return executeWithProvider(agent, input, savedExecution, modelOverride)
            .doOnSuccess(result -> {
                // Update execution record with results
                savedExecution.setStatus("COMPLETED");
                savedExecution.setOutputDataJson(result.output);
                savedExecution.setCompletedAt(LocalDateTime.now());
                savedExecution.setTokensUsed(result.inputTokens + result.outputTokens);
                savedExecution.setExecutionTimeMs(
                    (int) java.time.Duration.between(savedExecution.getStartedAt(), LocalDateTime.now()).toMillis());
                agentExecutionRepository.save(savedExecution);
                log.info("Execution {} completed successfully", savedExecution.getId());
            })
            .doOnError(error -> {
                // Update execution record with error
                savedExecution.setStatus("FAILED");
                savedExecution.setErrorMessage(error.getMessage());
                savedExecution.setCompletedAt(LocalDateTime.now());
                agentExecutionRepository.save(savedExecution);
                log.error("Execution {} failed: {}", savedExecution.getId(), error.getMessage());
            });
    }

    /**
     * Execute agent with the appropriate LLM provider
     */
    private Mono<AgentExecutionResult> executeWithProvider(Agent agent, JsonNode input, AgentExecution execution, String modelOverride) {
        String provider = agent.getModelProvider();
        log.info("Using provider: {} with model: {}", provider, agent.getModelName());

        switch (provider.toUpperCase()) {
            case "ANTHROPIC":
            case "CLAUDE":
                return executeWithClaude(agent, input, execution, modelOverride);
            case "OPENAI":
            case "GPT":
                return Mono.error(new UnsupportedOperationException("OpenAI provider not yet implemented"));
            case "GOOGLE":
            case "GEMINI":
                return Mono.error(new UnsupportedOperationException("Gemini provider not yet implemented"));
            default:
                return Mono.error(new IllegalArgumentException("Unknown provider: " + provider));
        }
    }

    /**
     * Execute agent using Claude API
     */
    private Mono<AgentExecutionResult> executeWithClaude(Agent agent, JsonNode input, AgentExecution execution, String modelOverride) {
        if (anthropicApiKey == null || anthropicApiKey.trim().isEmpty()) {
            return Mono.error(new IllegalStateException("Anthropic API key not configured"));
        }

        // Prefer the delegation/override model when supplied, else the agent's own configured model.
        String requestedModel = (modelOverride != null && !modelOverride.isBlank())
            ? modelOverride : agent.getModelName();

        // Validate model before using it
        String validatedModel = modelValidationService.validateOrDefault(requestedModel);
        if (!validatedModel.equals(requestedModel)) {
            log.warn("Invalid model '{}' for agent '{}', using '{}' instead",
                requestedModel, agent.getName(), validatedModel);
        }

        WebClient webClient = webClientBuilder
            .baseUrl("https://api.anthropic.com/v1")
            .build();

        // Build system prompt
        String systemPrompt = agent.getSystemPrompt();

        // Build messages array with input
        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");

        // Convert input JSON to string for the message
        String inputText;
        if (input.has("task")) {
            // Agent invocation format - extract task field from {"task": "..."}
            inputText = input.get("task").asText();
        } else if (input.isTextual()) {
            inputText = input.asText();
        } else {
            inputText = input.toString();
        }
        userMessage.put("content", inputText);
        messages.add(userMessage);

        // Load tools for this agent
        ArrayNode tools = buildToolsArrayForAgent(agent);

        // Call Claude API
        return callClaudeWithTools(webClient, validatedModel, agent, systemPrompt, messages, tools, 0, execution.getId());
    }

    /**
     * Call Claude API with tool support (recursive for multi-turn conversations)
     */
    private Mono<AgentExecutionResult> callClaudeWithTools(
            WebClient webClient,
            String validatedModel,
            Agent agent,
            String systemPrompt,
            ArrayNode messages,
            ArrayNode tools,
            int iteration,
            Long executionId) {

        if (iteration >= maxAgentIterations) {
            log.warn("Max tool use iterations ({}) reached for execution {}", maxAgentIterations, executionId);
            return Mono.error(new RuntimeException("Max iterations reached"));
        }

        // Create request body with validated model
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", validatedModel);
        requestBody.put("max_tokens", agent.getMaxTokens());
        requestBody.put("temperature", agent.getTemperature().doubleValue());

        // Prompt caching: send the system prompt as a cacheable block so the (unchanging) agent
        // instructions + tool context aren't re-processed on every iteration. Mirrors the main chat loop.
        ArrayNode systemBlocks = objectMapper.createArrayNode();
        ObjectNode systemBlock = objectMapper.createObjectNode();
        systemBlock.put("type", "text");
        systemBlock.put("text", systemPrompt);
        ObjectNode cacheControl = objectMapper.createObjectNode();
        cacheControl.put("type", "ephemeral");
        systemBlock.set("cache_control", cacheControl);
        systemBlocks.add(systemBlock);
        requestBody.set("system", systemBlocks);
        requestBody.set("messages", messages);

        if (tools.size() > 0) {
            requestBody.set("tools", tools);
        }

        log.debug("Calling Claude API - iteration: {}, model: {}", iteration, agent.getModelName());

        // Call Claude API
        return webClient.post()
            .uri("/messages")
            .header("x-api-key", anthropicApiKey)
            .header("anthropic-version", anthropicApiVersion)
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .flatMap(response -> handleClaudeResponse(
                webClient, validatedModel, agent, systemPrompt, messages, tools, response, iteration, executionId));
    }

    /**
     * Handle Claude API response - extract result or process tool calls
     */
    private Mono<AgentExecutionResult> handleClaudeResponse(
            WebClient webClient,
            String validatedModel,
            Agent agent,
            String systemPrompt,
            ArrayNode messages,
            ArrayNode tools,
            JsonNode response,
            int iteration,
            Long executionId) {

        try {
            String stopReason = response.get("stop_reason").asText();
            JsonNode content = response.get("content");

            // Extract token usage
            JsonNode usage = response.get("usage");
            int inputTokens = usage != null && usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0;
            int outputTokens = usage != null && usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0;

            log.debug("Claude response - stop_reason: {}, tokens: {}/{}", stopReason, inputTokens, outputTokens);

            if ("tool_use".equals(stopReason)) {
                // Handle tool calls and continue
                return handleToolUseAndContinue(
                    webClient, validatedModel, agent, systemPrompt, messages, tools, response, iteration, executionId);
            } else if ("pause_turn".equals(stopReason)) {
                // Native server tool (e.g. web_search) paused mid-turn — append the
                // assistant turn so far and let Claude continue where it left off.
                ObjectNode assistantMessage = objectMapper.createObjectNode();
                assistantMessage.put("role", "assistant");
                assistantMessage.set("content", response.get("content"));
                messages.add(assistantMessage);
                return callClaudeWithTools(
                    webClient, validatedModel, agent, systemPrompt, messages, tools, iteration + 1, executionId);
            } else {
                // Extract final response
                return extractFinalResult(response, inputTokens, outputTokens);
            }
        } catch (Exception e) {
            log.error("Error handling Claude response: {}", e.getMessage(), e);
            return Mono.error(e);
        }
    }

    /**
     * Handle tool use: execute tools and continue conversation
     */
    private Mono<AgentExecutionResult> handleToolUseAndContinue(
            WebClient webClient,
            String validatedModel,
            Agent agent,
            String systemPrompt,
            ArrayNode messages,
            ArrayNode tools,
            JsonNode response,
            int iteration,
            Long executionId) {

        // Collect client tool_use blocks first. Native server tools (server_tool_use,
        // e.g. web_search) are run by Anthropic and must not be executed here.
        JsonNode content = response.get("content");
        List<Mono<ObjectNode>> toolCallMonos = new ArrayList<>();

        // Add assistant's message with tool_use to messages
        ObjectNode assistantMessage = objectMapper.createObjectNode();
        assistantMessage.put("role", "assistant");
        assistantMessage.set("content", content);
        messages.add(assistantMessage);

        for (JsonNode block : content) {
            if ("tool_use".equals(block.get("type").asText())) {
                String toolName = block.get("name").asText();
                String toolUseId = block.get("id").asText();
                JsonNode toolInput = block.get("input");

                log.info("Executing tool: {} for execution {}", toolName, executionId);

                Mono<ObjectNode> toolResultMono = executeToolCall(toolName, toolInput, agent)
                    .map(toolResult -> {
                        ObjectNode toolResultBlock = objectMapper.createObjectNode();
                        toolResultBlock.put("type", "tool_result");
                        toolResultBlock.put("tool_use_id", toolUseId);
                        toolResultBlock.put("content", toolResult);
                        return toolResultBlock;
                    });

                toolCallMonos.add(toolResultMono);
            }
        }

        // No client tools to run (e.g. only server-tool blocks) — nothing to fulfil;
        // return whatever text Claude has produced so far.
        if (toolCallMonos.isEmpty()) {
            JsonNode usage = response.get("usage");
            int it = usage != null && usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0;
            int ot = usage != null && usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0;
            return extractFinalResult(response, it, ot);
        }

        // Execute all tools and continue conversation
        return Mono.zip(toolCallMonos, results -> {
            ArrayNode toolResults = objectMapper.createArrayNode();
            for (Object result : results) {
                toolResults.add((ObjectNode) result);
            }
            return toolResults;
        }).flatMap(toolResults -> {
            // Add tool results as user message
            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.set("content", toolResults);
            messages.add(userMessage);

            // Continue conversation
            return callClaudeWithTools(
                webClient, validatedModel, agent, systemPrompt, messages, tools, iteration + 1, executionId);
        });
    }

    /**
     * Execute a tool call
     *
     * - mcp_call: Call external MCP server tools
     * - Any other tool: look up its Tool record, resolve the Spring bean named by
     *   handler_class, validate input, and run handler.execute(). Errors are
     *   returned as JSON (never thrown) so Claude can react and the turn continues.
     *
     * Note: native Anthropic server tools (e.g. web_search, type=BUILTIN) are
     * executed by Anthropic itself and never reach this method.
     */
    private Mono<String> executeToolCall(String toolName, JsonNode input, Agent agent) {
        log.info("Tool execution: {} with input: {}", toolName, input);

        // Handle MCP tool calls
        if ("mcp_call".equals(toolName)) {
            return executeMCPToolCall(input);
        }

        return Mono.defer(() -> {
            Tool tool = toolRegistryService.getToolByName(toolName).orElse(null);
            if (tool == null || tool.getHandlerClass() == null || tool.getHandlerClass().isBlank()) {
                log.warn("No handler registered for tool '{}'", toolName);
                return Mono.just(toolErrorJson("No handler registered for tool '" + toolName + "'"));
            }

            ToolHandler handler;
            try {
                Class<?> handlerClass = Class.forName(tool.getHandlerClass());
                handler = (ToolHandler) applicationContext.getBean(handlerClass);
            } catch (Exception e) {
                log.error("Could not load tool handler '{}' for tool '{}': {}",
                    tool.getHandlerClass(), toolName, e.getMessage());
                return Mono.just(toolErrorJson("Tool handler not available for '" + toolName + "'"));
            }

            if (!handler.validateInput(input)) {
                return Mono.just(toolErrorJson("Invalid input for tool '" + toolName + "'"));
            }

            return handler.execute(input)
                .map(JsonNode::toString)
                .onErrorResume(err -> {
                    log.error("Tool '{}' execution failed: {}", toolName, err.getMessage());
                    return Mono.just(toolErrorJson("Tool '" + toolName + "' failed: " + err.getMessage()));
                });
        });
    }

    /** Build a safe JSON error string for a failed tool call. */
    private String toolErrorJson(String message) {
        ObjectNode err = objectMapper.createObjectNode();
        err.put("error", message);
        return err.toString();
    }

    /**
     * Execute an MCP tool call
     *
     * Expected input format:
     * {
     *   "tool_name": "name_of_mcp_tool",
     *   "arguments": { ... }
     * }
     */
    private Mono<String> executeMCPToolCall(JsonNode input) {
        try {
            // Extract tool_name and arguments from input
            if (!input.has("tool_name")) {
                return Mono.error(new IllegalArgumentException("MCP call missing 'tool_name' field"));
            }

            String mcpToolName = input.get("tool_name").asText();
            JsonNode argumentsNode = input.has("arguments") ? input.get("arguments") : objectMapper.createObjectNode();

            // Convert arguments to Map
            Map<String, Object> arguments = new HashMap<>();
            argumentsNode.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    arguments.put(entry.getKey(), value.asText());
                } else if (value.isNumber()) {
                    arguments.put(entry.getKey(), value.numberValue());
                } else if (value.isBoolean()) {
                    arguments.put(entry.getKey(), value.booleanValue());
                } else if (value.isNull()) {
                    arguments.put(entry.getKey(), null);
                } else {
                    // For complex types, pass as JsonNode
                    arguments.put(entry.getKey(), value);
                }
            });

            log.info("Calling MCP tool: {} with arguments: {}", mcpToolName, arguments);

            // Call MCP client
            return mcpClient.callTool(mcpToolName, arguments)
                .map(result -> result.toString())
                .doOnSuccess(result -> log.info("MCP tool {} completed", mcpToolName))
                .doOnError(error -> log.error("MCP tool {} failed: {}", mcpToolName, error.getMessage()));

        } catch (Exception e) {
            log.error("Error executing MCP tool call: {}", e.getMessage(), e);
            return Mono.error(new RuntimeException("Failed to execute MCP tool: " + e.getMessage(), e));
        }
    }

    /**
     * Build tools array for agent from agent_tools relationship
     */
    private ArrayNode buildToolsArrayForAgent(Agent agent) {
        ArrayNode tools = objectMapper.createArrayNode();

        // Load assignments via the repository with an explicit fetch-join rather than
        // agent.getAgentTools(): the lazy collection can come back empty here (the
        // call assembles a reactive pipeline, detaching from the JPA session).
        List<AgentTool> agentTools = agentToolRepository.findByAgentIdWithTool(agent.getId());
        for (AgentTool agentTool : agentTools) {
            Tool tool = agentTool.getTool();

            if (!tool.getIsActive()) {
                continue;
            }

            // Native Anthropic server tools (e.g. web_search). For these, input_schema_json
            // already holds the full server-tool definition (e.g. {"type":"web_search_20250305",
            // "name":"web_search","max_uses":5}); emit it verbatim. Anthropic executes them.
            if ("BUILTIN".equalsIgnoreCase(tool.getType())) {
                tools.add(tool.getInputSchemaJson());
                log.debug("Added native server tool: {} to agent: {}", tool.getName(), agent.getName());
                continue;
            }

            ObjectNode toolDef = objectMapper.createObjectNode();
            toolDef.put("name", tool.getName());
            toolDef.put("description", tool.getDescription());
            toolDef.set("input_schema", tool.getInputSchemaJson());

            tools.add(toolDef);
            log.debug("Added tool: {} to agent: {}", tool.getName(), agent.getName());
        }

        log.info("Agent {} has {} active tools", agent.getName(), tools.size());
        return tools;
    }

    /**
     * Extract final result from Claude response
     */
    private Mono<AgentExecutionResult> extractFinalResult(JsonNode response, int inputTokens, int outputTokens) {
        try {
            JsonNode content = response.get("content");
            StringBuilder textBuilder = new StringBuilder();

            if (content != null && content.isArray()) {
                for (JsonNode block : content) {
                    if ("text".equals(block.get("type").asText())) {
                        textBuilder.append(block.get("text").asText());
                    }
                }
            }

            // Build output JSON
            ObjectNode output = objectMapper.createObjectNode();
            output.put("text", textBuilder.toString());
            output.put("stop_reason", response.get("stop_reason").asText());

            AgentExecutionResult result = AgentExecutionResult.builder()
                .output(output)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .success(true)
                .build();

            return Mono.just(result);

        } catch (Exception e) {
            log.error("Error extracting final result: {}", e.getMessage(), e);
            return Mono.error(e);
        }
    }

    /**
     * Result of agent execution
     */
    @lombok.Data
    @lombok.Builder
    public static class AgentExecutionResult {
        private JsonNode output;
        private int inputTokens;
        private int outputTokens;
        private boolean success;
        private String errorMessage;

        /**
         * Calculate approximate cost based on Claude pricing
         * TODO: Make this configurable per model
         */
        public BigDecimal calculateCost() {
            // Claude 3.5 Sonnet pricing (as of 2024): $3 per MTok input, $15 per MTok output
            double inputCost = (inputTokens / 1_000_000.0) * 3.0;
            double outputCost = (outputTokens / 1_000_000.0) * 15.0;
            return BigDecimal.valueOf(inputCost + outputCost);
        }
    }
}
