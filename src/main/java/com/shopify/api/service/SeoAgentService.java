package com.shopify.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.model.ChatMessage;
import com.shopify.api.model.SeoAgentRequest;
import com.shopify.api.model.SeoAgentResponse;
import com.shopify.api.model.agent.Agent;
import com.shopify.api.model.agent.Tool;
import com.shopify.api.repository.agent.AgentRepository;
import com.shopify.api.repository.agent.ToolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private final ObjectMapper objectMapper;

    @Autowired
    public SeoAgentService(WebClient.Builder webClientBuilder,
                          ToolRepository toolRepository,
                          AgentRepository agentRepository) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.anthropic.com/v1")
                .build();
        this.toolRepository = toolRepository;
        this.agentRepository = agentRepository;
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

        // Add tools if selected
        if (request.getConfig() != null && request.getConfig().getSelectedTools() != null
                && !request.getConfig().getSelectedTools().isEmpty()) {
            ArrayNode toolsArray = buildToolsArray(request.getConfig().getSelectedTools());
            if (toolsArray.size() > 0) {
                requestBody.set("tools", toolsArray);
            }
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

            // Execute all tool calls
            JsonNode content = response.get("content");
            ArrayNode toolResults = objectMapper.createArrayNode();

            for (JsonNode block : content) {
                if ("tool_use".equals(block.get("type").asText())) {
                    String toolName = block.get("name").asText();
                    String toolUseId = block.get("id").asText();
                    JsonNode toolInput = block.get("input");

                    logger.info("Executing tool: {}", toolName);
                    toolsUsed.add(toolName);

                    // Build tool result block
                    ObjectNode toolResultBlock = objectMapper.createObjectNode();
                    toolResultBlock.put("type", "tool_result");
                    toolResultBlock.put("tool_use_id", toolUseId);
                    toolResultBlock.put("content", "Tool executed: " + toolName + " (integration pending)");
                    toolResults.add(toolResultBlock);
                }
            }

            // Add user message with tool results
            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.set("content", toolResults);
            messages.add(userMessage);

            // Continue conversation
            return callClaudeWithTools(request, systemPrompt, messages, iteration + 1,
                    startTime, toolsUsed, agentsInvoked);

        } catch (Exception e) {
            logger.error("Error handling tool use: {}", e.getMessage());
            return Mono.just(createErrorResponse(startTime));
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
