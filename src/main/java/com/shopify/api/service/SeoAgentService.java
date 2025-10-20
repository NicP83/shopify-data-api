package com.shopify.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.model.ChatMessage;
import com.shopify.api.model.SeoAgentRequest;
import com.shopify.api.model.SeoAgentResponse;
import com.shopify.api.client.MCPClient;
import com.shopify.api.model.agent.Agent;
import com.shopify.api.model.agent.Tool;
import com.shopify.api.repository.agent.AgentRepository;
import com.shopify.api.repository.agent.ToolRepository;
import com.shopify.api.service.agent.AgentExecutionService;
import com.shopify.api.handler.tool.ToolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * SEO Agent Service - Orchestrates AI-powered SEO tasks with configurable tools and agents
 *
 * Provides a flexible chat interface where users can:
 * - Select which tools to make available
 * - Choose which agents can be invoked
 * - Configure LLM settings (model, temperature, etc.)
 * - Define custom orchestration prompts
 *
 * See: docs/seo-agent/IMPLEMENTATION_PLAN.md for feature details
 */
@Service
public class SeoAgentService {

    private static final Logger logger = LoggerFactory.getLogger(SeoAgentService.class);

    @Value("${anthropic.api.key:}")
    private String anthropicApiKey;

    @Value("${anthropic.api.version:2023-06-01}")
    private String anthropicApiVersion;

    private final WebClient webClient;
    private final ToolRepository toolRepository;
    private final AgentRepository agentRepository;
    private final AgentExecutionService agentExecutionService;
    private final MCPClient mcpClient;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    @Autowired
    public SeoAgentService(WebClient.Builder webClientBuilder,
                          ToolRepository toolRepository,
                          AgentRepository agentRepository,
                          AgentExecutionService agentExecutionService,
                          MCPClient mcpClient,
                          ApplicationContext applicationContext) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.anthropic.com/v1")
                .build();
        this.toolRepository = toolRepository;
        this.agentRepository = agentRepository;
        this.agentExecutionService = agentExecutionService;
        this.mcpClient = mcpClient;
        this.applicationContext = applicationContext;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Process SEO agent chat with configurable tools, agents, and LLM settings
     */
    public Mono<SeoAgentResponse> processChat(SeoAgentRequest request) {
        logger.info("Processing SEO agent message: {}", request.getMessage());

        long startTime = System.currentTimeMillis();

        // Check if API key is configured
        if (anthropicApiKey == null || anthropicApiKey.trim().isEmpty()) {
            logger.warn("Anthropic API key not configured");
            return Mono.just(createMockResponse(request.getMessage(), startTime));
        }

        // Build system prompt from configuration
        String systemPrompt = buildSystemPrompt(request);

        // Build messages array
        ArrayNode messages = buildMessagesArray(request);

        // Call Claude API with configured tools
        return callClaudeWithTools(request, systemPrompt, messages, 0, startTime, new ArrayList<>(), new ArrayList<>());
    }

    /**
     * Recursively call Claude API with tool support
     */
    private Mono<SeoAgentResponse> callClaudeWithTools(
            SeoAgentRequest request,
            String systemPrompt,
            ArrayNode messages,
            int iteration,
            long startTime,
            List<String> toolsUsed,
            List<String> agentsInvoked) {

        if (iteration >= 5) {
            logger.warn("Max tool use iterations reached");
            return Mono.just(createErrorResponse(startTime));
        }

        // Get LLM config or use defaults
        SeoAgentRequest.LlmConfig llmConfig = request.getConfig() != null && request.getConfig().getLlmConfig() != null
                ? request.getConfig().getLlmConfig()
                : SeoAgentRequest.LlmConfig.builder().build();

        // Create request body for Claude API
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", llmConfig.getModel());
        requestBody.put("max_tokens", llmConfig.getMaxTokens());
        requestBody.put("temperature", llmConfig.getTemperature());
        requestBody.put("top_p", llmConfig.getTopP());
        requestBody.put("system", systemPrompt);
        requestBody.set("messages", messages);

        // Add tools and agents if selected
        ArrayNode toolsArray = objectMapper.createArrayNode();

        // Add selected tools
        if (request.getConfig() != null && request.getConfig().getSelectedTools() != null
                && !request.getConfig().getSelectedTools().isEmpty()) {
            ArrayNode selectedTools = buildToolsArray(request.getConfig().getSelectedTools());
            selectedTools.forEach(toolsArray::add);
        }

        // Add selected agents as invocation tools
        if (request.getConfig() != null && request.getConfig().getSelectedAgents() != null
                && !request.getConfig().getSelectedAgents().isEmpty()) {
            ArrayNode agentTools = buildAgentInvocationTools(request.getConfig().getSelectedAgents());
            agentTools.forEach(toolsArray::add);
        }

        if (toolsArray.size() > 0) {
            requestBody.set("tools", toolsArray);
        }

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
                        request, response, systemPrompt, messages, iteration, startTime, toolsUsed, agentsInvoked))
                .onErrorResume(error -> {
                    logger.error("Error calling Claude API: {}", error.getMessage());
                    return Mono.just(createErrorResponse(startTime));
                });
    }

    /**
     * Handle Claude API response - extract message or handle tool use
     */
    private Mono<SeoAgentResponse> handleClaudeResponse(
            SeoAgentRequest request,
            JsonNode response,
            String systemPrompt,
            ArrayNode messages,
            int iteration,
            long startTime,
            List<String> toolsUsed,
            List<String> agentsInvoked) {

        try {
            String stopReason = response.get("stop_reason").asText();
            JsonNode content = response.get("content");

            if ("tool_use".equals(stopReason) && content != null && content.isArray()) {
                // Claude wants to use tools
                logger.info("Claude requested tool use");
                return handleToolUseAndContinue(request, response, systemPrompt, messages,
                        iteration, startTime, toolsUsed, agentsInvoked);
            } else if ("end_turn".equals(stopReason)) {
                // Regular text response
                logger.debug("Claude completed turn");
                return Mono.just(createSuccessResponse(response, startTime, toolsUsed, agentsInvoked));
            } else {
                logger.warn("Unexpected stop_reason: {}", stopReason);
                return Mono.just(createSuccessResponse(response, startTime, toolsUsed, agentsInvoked));
            }
        } catch (Exception e) {
            logger.error("Error handling Claude response: {}", e.getMessage(), e);
            return Mono.just(createErrorResponse(startTime));
        }
    }

    /**
     * Execute tool calls and continue conversation
     */
    private Mono<SeoAgentResponse> handleToolUseAndContinue(
            SeoAgentRequest request,
            JsonNode response,
            String systemPrompt,
            ArrayNode messages,
            int iteration,
            long startTime,
            List<String> toolsUsed,
            List<String> agentsInvoked) {

        try {
            // Add assistant's response with tool_use to messages
            ObjectNode assistantMessage = objectMapper.createObjectNode();
            assistantMessage.put("role", "assistant");
            assistantMessage.set("content", response.get("content"));
            messages.add(assistantMessage);

            // Execute all tool calls and collect results
            JsonNode content = response.get("content");
            List<Mono<ObjectNode>> toolCallMonos = new ArrayList<>();

            for (JsonNode block : content) {
                if ("tool_use".equals(block.get("type").asText())) {
                    String toolName = block.get("name").asText();
                    String toolUseId = block.get("id").asText();
                    JsonNode toolInput = block.get("input");

                    logger.info("Executing tool: {}", toolName);
                    toolsUsed.add(toolName);

                    // Execute tool and build result block
                    Mono<ObjectNode> toolResultMono = executeToolCall(toolName, toolInput, request)
                            .map(toolResult -> {
                                ObjectNode toolResultBlock = objectMapper.createObjectNode();
                                toolResultBlock.put("type", "tool_result");
                                toolResultBlock.put("tool_use_id", toolUseId);
                                toolResultBlock.put("content", toolResult);
                                return toolResultBlock;
                            })
                            .onErrorResume(error -> {
                                logger.error("Tool execution error for {}: {}", toolName, error.getMessage());
                                ObjectNode errorBlock = objectMapper.createObjectNode();
                                errorBlock.put("type", "tool_result");
                                errorBlock.put("tool_use_id", toolUseId);
                                errorBlock.put("content", "Tool execution failed: " + error.getMessage());
                                errorBlock.put("is_error", true);
                                return Mono.just(errorBlock);
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
                // Add user message with tool results
                ObjectNode userMessage = objectMapper.createObjectNode();
                userMessage.put("role", "user");
                userMessage.set("content", toolResults);
                messages.add(userMessage);

                // Continue conversation
                return callClaudeWithTools(request, systemPrompt, messages, iteration + 1,
                        startTime, toolsUsed, agentsInvoked);
            });

        } catch (Exception e) {
            logger.error("Error handling tool use: {}", e.getMessage());
            return Mono.just(createErrorResponse(startTime));
        }
    }

    /**
     * Execute a single tool call
     * Routes to appropriate execution based on tool type:
     * - Agent invocation: execute via AgentExecutionService.executeAgent
     * - MCP tools: call via MCPClient
     * - Custom tools: load handler class dynamically
     */
    private Mono<String> executeToolCall(String toolName, JsonNode toolInput, SeoAgentRequest request) {
        logger.info("Executing tool: {} with input: {}", toolName, toolInput);

        // Check if this is an agent invocation (pattern: invoke_agent_{id})
        if (toolName.startsWith("invoke_agent_")) {
            try {
                Long agentId = Long.parseLong(toolName.substring("invoke_agent_".length()));
                logger.info("Invoking agent with ID: {}", agentId);
                return executeAgentInvocation(agentId, toolInput, request);
            } catch (NumberFormatException e) {
                logger.error("Invalid agent ID in tool name: {}", toolName);
                return Mono.just("{\"error\": \"Invalid agent ID in tool name\"}");
            }
        }

        // Look up tool from database
        return Mono.fromCallable(() -> toolRepository.findAll().stream()
                .filter(t -> t.getName().equals(toolName))
                .findFirst()
                .orElse(null))
            .flatMap(tool -> {
                if (tool == null) {
                    logger.warn("Tool not found: {}", toolName);
                    return Mono.just("{\"error\": \"Tool not found: " + toolName + "\"}");
                }

                String toolType = tool.getType();
                logger.info("Tool type: {}", toolType);

                // Route based on tool type
                if ("MCP".equals(toolType)) {
                    // MCP tools - call via MCPClient
                    return executeMCPTool(toolInput);
                } else {
                    // Custom tools (SHOPIFY, DATABASE, API, etc.)
                    // Load and execute handler class dynamically
                    return executeCustomTool(tool, toolInput);
                }
            });
    }

    /**
     * Execute an agent invocation
     * Allows the SEO Agent to orchestrate other agents as sub-tasks
     */
    private Mono<String> executeAgentInvocation(Long agentId, JsonNode toolInput, SeoAgentRequest request) {
        logger.info("Executing agent invocation for agent ID: {} with input: {}", agentId, toolInput);

        // Track agent invocation if needed
        if (request != null) {
            // Could add agent name to agentsInvoked list here if we had access to it
        }

        // Execute the agent
        return agentExecutionService.executeAgent(agentId, toolInput)
                .map(result -> {
                    // Extract text output from agent result
                    JsonNode output = result.getOutput();
                    if (output.has("text")) {
                        return output.get("text").asText();
                    }
                    return output.toString();
                })
                .doOnSuccess(result -> logger.info("Agent {} execution completed successfully", agentId))
                .onErrorResume(error -> {
                    logger.error("Agent {} execution failed: {}", agentId, error.getMessage());
                    return Mono.just("{\"error\": \"Agent execution failed: " + error.getMessage() + "\"}");
                });
    }

    /**
     * Execute an MCP tool call
     * Uses MCPClient to call external MCP server tools
     */
    private Mono<String> executeMCPTool(JsonNode toolInput) {
        try {
            // MCP tools expect tool_name and arguments fields
            if (!toolInput.has("tool_name")) {
                return Mono.error(new IllegalArgumentException("MCP tool call missing 'tool_name' field"));
            }

            String mcpToolName = toolInput.get("tool_name").asText();
            JsonNode argumentsNode = toolInput.has("arguments") ? toolInput.get("arguments") : objectMapper.createObjectNode();

            // Convert arguments to Map<String, Object>
            java.util.Map<String, Object> arguments = new java.util.HashMap<>();
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

            logger.info("Calling MCP tool: {} with arguments: {}", mcpToolName, arguments);

            // Call MCP client directly
            return mcpClient.callTool(mcpToolName, arguments)
                    .map(result -> result.toString())
                    .doOnSuccess(result -> logger.info("MCP tool {} completed successfully", mcpToolName))
                    .onErrorResume(error -> {
                        logger.error("MCP tool {} failed: {}", mcpToolName, error.getMessage());
                        return Mono.just("{\"error\": \"" + error.getMessage() + "\"}");
                    });
        } catch (Exception e) {
            logger.error("Error preparing MCP tool call: {}", e.getMessage(), e);
            return Mono.error(new RuntimeException("Failed to execute MCP tool: " + e.getMessage(), e));
        }
    }

    /**
     * Execute a custom tool by dynamically loading its handler class
     * Uses reflection to instantiate the handler and Spring's ApplicationContext to get beans
     */
    private Mono<String> executeCustomTool(Tool tool, JsonNode toolInput) {
        logger.info("Executing custom tool: {} with handler: {}", tool.getName(), tool.getHandlerClass());

        try {
            // Load handler class via reflection
            Class<?> handlerClass = Class.forName(tool.getHandlerClass());

            // Check if class implements ToolHandler interface
            if (!ToolHandler.class.isAssignableFrom(handlerClass)) {
                logger.error("Handler class {} does not implement ToolHandler interface", tool.getHandlerClass());
                return Mono.just("{\"error\": \"Invalid tool handler class\"}");
            }

            // Try to get handler bean from Spring context first (if it's a @Component)
            ToolHandler handler;
            try {
                handler = (ToolHandler) applicationContext.getBean(handlerClass);
                logger.debug("Retrieved handler {} from Spring context", tool.getHandlerClass());
            } catch (Exception e) {
                // If not found in context, try to instantiate manually
                logger.debug("Handler {} not found in Spring context, attempting manual instantiation", tool.getHandlerClass());
                handler = (ToolHandler) handlerClass.getDeclaredConstructor().newInstance();
            }

            // Validate input
            if (!handler.validateInput(toolInput)) {
                logger.warn("Tool input validation failed for tool: {}", tool.getName());
                return Mono.just("{\"error\": \"Invalid input for tool: " + tool.getName() + "\"}");
            }

            // Execute tool and convert JsonNode result to String
            return handler.execute(toolInput)
                    .map(result -> result.toString())
                    .doOnSuccess(result -> logger.info("Custom tool {} executed successfully", tool.getName()))
                    .onErrorResume(error -> {
                        logger.error("Custom tool {} execution failed: {}", tool.getName(), error.getMessage());
                        return Mono.just("{\"error\": \"" + error.getMessage() + "\"}");
                    });

        } catch (ClassNotFoundException e) {
            logger.error("Tool handler class not found: {}", tool.getHandlerClass());
            return Mono.just("{\"error\": \"Tool handler class not found: " + tool.getHandlerClass() + "\"}");
        } catch (Exception e) {
            logger.error("Error loading tool handler: {}", e.getMessage(), e);
            return Mono.just("{\"error\": \"Failed to load tool handler: " + e.getMessage() + "\"}");
        }
    }

    /**
     * Build system prompt from request configuration
     */
    private String buildSystemPrompt(SeoAgentRequest request) {
        StringBuilder prompt = new StringBuilder();

        // Use custom orchestration prompt if provided
        if (request.getConfig() != null && request.getConfig().getOrchestrationPrompt() != null
                && !request.getConfig().getOrchestrationPrompt().trim().isEmpty()) {
            prompt.append(request.getConfig().getOrchestrationPrompt());
        } else {
            // Default SEO agent prompt
            prompt.append("You are an advanced SEO optimization assistant with access to various tools and capabilities.\n\n");
            prompt.append("Your primary role is to help with SEO-related tasks including:\n");
            prompt.append("- Product description optimization\n");
            prompt.append("- Meta tag generation\n");
            prompt.append("- Keyword research and analysis\n");
            prompt.append("- Content strategy recommendations\n");
            prompt.append("- Search visibility improvements\n\n");
            prompt.append("When available, use the provided tools to gather information and perform tasks.\n");
            prompt.append("Always provide actionable, data-driven recommendations.");
        }

        // Add tool context if tools are selected
        if (request.getConfig() != null && request.getConfig().getSelectedTools() != null
                && !request.getConfig().getSelectedTools().isEmpty()) {
            prompt.append("\n\n=== AVAILABLE TOOLS ===\n");
            prompt.append("You have access to the following tools. Use them when appropriate:\n");

            List<Tool> selectedTools = toolRepository.findAllById(request.getConfig().getSelectedTools());
            for (Tool tool : selectedTools) {
                prompt.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
            }
        }

        // Add agent context if agents are selected
        if (request.getConfig() != null && request.getConfig().getSelectedAgents() != null
                && !request.getConfig().getSelectedAgents().isEmpty()) {
            prompt.append("\n\n=== AVAILABLE AGENTS ===\n");
            prompt.append("You can invoke the following specialized agents for complex tasks:\n");

            List<Agent> selectedAgents = agentRepository.findAllById(request.getConfig().getSelectedAgents());
            for (Agent agent : selectedAgents) {
                prompt.append("- ").append(agent.getName()).append(": ").append(agent.getDescription()).append("\n");
            }
        }

        return prompt.toString();
    }

    /**
     * Build messages array from conversation history
     */
    private ArrayNode buildMessagesArray(SeoAgentRequest request) {
        ArrayNode messages = objectMapper.createArrayNode();

        // Add conversation history
        if (request.getConversationHistory() != null) {
            for (ChatMessage msg : request.getConversationHistory()) {
                ObjectNode messageNode = objectMapper.createObjectNode();
                messageNode.put("role", msg.getRole());
                messageNode.put("content", msg.getContent());
                messages.add(messageNode);
            }
        }

        // Add current user message
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", request.getMessage());
        messages.add(userMessage);

        return messages;
    }

    /**
     * Build tools array from selected tool IDs
     */
    private ArrayNode buildToolsArray(List<Long> selectedToolIds) {
        ArrayNode tools = objectMapper.createArrayNode();

        List<Tool> selectedTools = toolRepository.findAllById(selectedToolIds);

        for (Tool tool : selectedTools) {
            if (tool.getIsActive()) {
                ObjectNode toolNode = objectMapper.createObjectNode();
                toolNode.put("name", tool.getName());
                toolNode.put("description", tool.getDescription());
                toolNode.set("input_schema", tool.getInputSchemaJson());
                tools.add(toolNode);
            }
        }

        return tools;
    }

    /**
     * Build agent invocation tools from selected agent IDs
     * Creates tool definitions that allow Claude to invoke other agents
     */
    private ArrayNode buildAgentInvocationTools(List<Long> selectedAgentIds) {
        ArrayNode tools = objectMapper.createArrayNode();

        List<Agent> selectedAgents = agentRepository.findAllById(selectedAgentIds);

        for (Agent agent : selectedAgents) {
            if (agent.getIsActive()) {
                ObjectNode toolNode = objectMapper.createObjectNode();
                toolNode.put("name", "invoke_agent_" + agent.getId());
                toolNode.put("description", "Invoke the " + agent.getName() + " agent: " + agent.getDescription());

                // Create input schema for agent invocation
                ObjectNode inputSchema = objectMapper.createObjectNode();
                inputSchema.put("type", "object");

                ObjectNode properties = objectMapper.createObjectNode();
                ObjectNode taskProperty = objectMapper.createObjectNode();
                taskProperty.put("type", "string");
                taskProperty.put("description", "The task or query to send to the agent");
                properties.set("task", taskProperty);

                inputSchema.set("properties", properties);

                ArrayNode required = objectMapper.createArrayNode();
                required.add("task");
                inputSchema.set("required", required);

                toolNode.set("input_schema", inputSchema);
                tools.add(toolNode);

                logger.debug("Added agent invocation tool: invoke_agent_{}", agent.getId());
            }
        }

        return tools;
    }

    /**
     * Extract assistant message from Claude API response
     */
    private ChatMessage extractAssistantMessage(JsonNode response) {
        try {
            JsonNode content = response.get("content");
            if (content != null && content.isArray() && content.size() > 0) {
                StringBuilder fullText = new StringBuilder();
                for (JsonNode block : content) {
                    if ("text".equals(block.get("type").asText())) {
                        fullText.append(block.get("text").asText());
                    }
                }
                if (fullText.length() > 0) {
                    return new ChatMessage("assistant", fullText.toString());
                }
            }
            return new ChatMessage("assistant", "No response generated");
        } catch (Exception e) {
            logger.error("Error extracting assistant message: {}", e.getMessage());
            return new ChatMessage("assistant", "Error processing response");
        }
    }

    /**
     * Create successful response
     */
    private SeoAgentResponse createSuccessResponse(JsonNode claudeResponse, long startTime,
            List<String> toolsUsed, List<String> agentsInvoked) {

        ChatMessage message = extractAssistantMessage(claudeResponse);
        long processingTime = System.currentTimeMillis() - startTime;

        // Extract token usage if available
        Integer tokensUsed = null;
        try {
            JsonNode usage = claudeResponse.get("usage");
            if (usage != null) {
                tokensUsed = usage.get("output_tokens").asInt() + usage.get("input_tokens").asInt();
            }
        } catch (Exception e) {
            logger.debug("Could not extract token usage: {}", e.getMessage());
        }

        SeoAgentResponse.ResponseMetadata metadata = SeoAgentResponse.ResponseMetadata.builder()
                .tokensUsed(tokensUsed)
                .processingTimeMs(processingTime)
                .modelUsed(claudeResponse.has("model") ? claudeResponse.get("model").asText() : "unknown")
                .build();

        return SeoAgentResponse.builder()
                .message(message)
                .toolsUsed(toolsUsed)
                .agentsInvoked(agentsInvoked)
                .metadata(metadata)
                .build();
    }

    /**
     * Create mock response when API key is not configured
     */
    private SeoAgentResponse createMockResponse(String userMessage, long startTime) {
        ChatMessage message = new ChatMessage("assistant",
                "Hello! I'm the SEO Agent. I would help you optimize product descriptions, generate meta tags, " +
                "and improve search visibility. However, the Anthropic API key is not configured. " +
                "Please set the ANTHROPIC_API_KEY environment variable to enable full functionality.");

        long processingTime = System.currentTimeMillis() - startTime;

        SeoAgentResponse.ResponseMetadata metadata = SeoAgentResponse.ResponseMetadata.builder()
                .processingTimeMs(processingTime)
                .modelUsed("mock")
                .build();

        return SeoAgentResponse.builder()
                .message(message)
                .toolsUsed(new ArrayList<>())
                .agentsInvoked(new ArrayList<>())
                .metadata(metadata)
                .build();
    }

    /**
     * Create error response
     */
    private SeoAgentResponse createErrorResponse(long startTime) {
        ChatMessage message = new ChatMessage("assistant",
                "I apologize, but I encountered an error processing your request. Please try again.");

        long processingTime = System.currentTimeMillis() - startTime;

        SeoAgentResponse.ResponseMetadata metadata = SeoAgentResponse.ResponseMetadata.builder()
                .processingTimeMs(processingTime)
                .modelUsed("error")
                .build();

        return SeoAgentResponse.builder()
                .message(message)
                .toolsUsed(new ArrayList<>())
                .agentsInvoked(new ArrayList<>())
                .metadata(metadata)
                .build();
    }
}
