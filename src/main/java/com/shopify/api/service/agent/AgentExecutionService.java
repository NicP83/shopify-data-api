package com.shopify.api.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.model.agent.Agent;
import com.shopify.api.model.agent.AgentExecution;
import com.shopify.api.model.agent.AgentTool;
import com.shopify.api.model.agent.Tool;
import com.shopify.api.repository.agent.AgentExecutionRepository;
import com.shopify.api.repository.agent.AgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private final AgentExecutionRepository agentExecutionRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.api.key:}")
    private String anthropicApiKey;

    @Value("${anthropic.api.version:2023-06-01}")
    private String anthropicApiVersion;

    /**
     * Execute an agent with given input and return structured result
     *
     * @param agentId The ID of the agent to execute
     * @param input   The input data for the agent (JSON)
     * @return Execution result with output, tokens used, and execution ID
     */
    @Transactional
    public Mono<AgentExecutionResult> executeAgent(Long agentId, JsonNode input) {
        log.info("Executing agent ID: {} with input", agentId);

        // Load agent from database
        return Mono.fromCallable(() -> agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found with ID: " + agentId)))
            .flatMap(agent -> {
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

                // Execute based on provider
                return executeWithProvider(agent, input, savedExecution)
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
            });
    }

    /**
     * Execute agent with the appropriate LLM provider
     */
    private Mono<AgentExecutionResult> executeWithProvider(Agent agent, JsonNode input, AgentExecution execution) {
        String provider = agent.getModelProvider();
        log.info("Using provider: {} with model: {}", provider, agent.getModelName());

        switch (provider.toUpperCase()) {
            case "ANTHROPIC":
            case "CLAUDE":
                return executeWithClaude(agent, input, execution);
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
    private Mono<AgentExecutionResult> executeWithClaude(Agent agent, JsonNode input, AgentExecution execution) {
        if (anthropicApiKey == null || anthropicApiKey.trim().isEmpty()) {
            return Mono.error(new IllegalStateException("Anthropic API key not configured"));
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
        if (input.isTextual()) {
            inputText = input.asText();
        } else {
            inputText = input.toString();
        }
        userMessage.put("content", inputText);
        messages.add(userMessage);

        // Load tools for this agent
        ArrayNode tools = buildToolsArrayForAgent(agent);

        // Call Claude API
        return callClaudeWithTools(webClient, agent, systemPrompt, messages, tools, 0, execution.getId());
    }

    /**
     * Call Claude API with tool support (recursive for multi-turn conversations)
     */
    private Mono<AgentExecutionResult> callClaudeWithTools(
            WebClient webClient,
            Agent agent,
            String systemPrompt,
            ArrayNode messages,
            ArrayNode tools,
            int iteration,
            Long executionId) {

        if (iteration >= 10) {
            log.warn("Max tool use iterations reached for execution {}", executionId);
            return Mono.error(new RuntimeException("Max iterations reached"));
        }

        // Create request body
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", agent.getModelName());
        requestBody.put("max_tokens", agent.getMaxTokens());
        requestBody.put("temperature", agent.getTemperature().doubleValue());
        requestBody.put("system", systemPrompt);
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
                webClient, agent, systemPrompt, messages, tools, response, iteration, executionId));
    }

    /**
     * Handle Claude API response - extract result or process tool calls
     */
    private Mono<AgentExecutionResult> handleClaudeResponse(
            WebClient webClient,
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
                    webClient, agent, systemPrompt, messages, tools, response, iteration, executionId);
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
            Agent agent,
            String systemPrompt,
            ArrayNode messages,
            ArrayNode tools,
            JsonNode response,
            int iteration,
            Long executionId) {

        // Add assistant's message with tool_use to messages
        ObjectNode assistantMessage = objectMapper.createObjectNode();
        assistantMessage.put("role", "assistant");
        assistantMessage.set("content", response.get("content"));
        messages.add(assistantMessage);

        // Execute tool calls
        JsonNode content = response.get("content");
        List<Mono<ObjectNode>> toolCallMonos = new ArrayList<>();

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
                webClient, agent, systemPrompt, messages, tools, iteration + 1, executionId);
        });
    }

    /**
     * Execute a tool call (stub for now - will be implemented with actual tool handlers)
     */
    private Mono<String> executeToolCall(String toolName, JsonNode input, Agent agent) {
        log.info("Tool execution: {} with input: {}", toolName, input);

        // TODO: Implement actual tool execution by loading tool handler classes
        // For now, return a placeholder
        return Mono.just("{\"message\": \"Tool '" + toolName + "' executed successfully\", \"input\": " + input.toString() + "}");
    }

    /**
     * Build tools array for agent from agent_tools relationship
     */
    private ArrayNode buildToolsArrayForAgent(Agent agent) {
        ArrayNode tools = objectMapper.createArrayNode();

        for (AgentTool agentTool : agent.getAgentTools()) {
            Tool tool = agentTool.getTool();

            if (!tool.getIsActive()) {
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
