package com.shopify.api.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.model.agent.Agent;
import com.shopify.api.repository.agent.AgentRepository;
import com.shopify.api.service.agent.AgentExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Tool handler for delegating tasks to specialist agents
 *
 * This handler enables the chatbot to route specialized questions to expert agents.
 * Each agent has its own system prompt, tools, and knowledge base.
 *
 * Input schema:
 * {
 *   "agent_name": "paint_expert",  // Name of the specialist agent
 *   "task": "What's the best paint for weathering tanks?"  // Task to delegate
 * }
 *
 * Returns:
 * {
 *   "agent": "paint_expert",
 *   "response": "For weathering tanks, I recommend...",
 *   "tokens_used": 523,
 *   "success": true
 * }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentDelegationToolHandler implements ToolHandler {

    private final AgentRepository agentRepository;
    private final AgentExecutionService agentExecutionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<JsonNode> execute(JsonNode input) {
        String agentName = input.get("agent_name").asText();
        String task = input.get("task").asText();

        log.info("=== DELEGATING TO AGENT ===");
        log.info("Agent: '{}', Task: '{}'", agentName, task);

        // Find agent by name (reactive wrapper for synchronous repository call)
        return Mono.fromCallable(() -> agentRepository.findByName(agentName))
                .flatMap(agentOpt -> {
                    if (agentOpt.isEmpty()) {
                        log.warn("Agent not found: {}", agentName);
                        return createErrorResponse("Agent '" + agentName + "' not found");
                    }

                    Agent agent = agentOpt.get();

                    // Check if agent is active
                    if (!agent.getIsActive()) {
                        log.warn("Agent is inactive: {}", agentName);
                        return createErrorResponse("Agent '" + agentName + "' is not active");
                    }

                    log.info("Delegating to agent ID: {}, Name: {}", agent.getId(), agent.getName());

                    // Build input JSON for agent execution
                    // Append product-first instruction so specialist agents always show products
                    String enrichedTask = task +
                        "\n\nIMPORTANT: You MUST search for and include real product recommendations " +
                        "with prices and Add to Cart links in your response. You can ask ONE brief " +
                        "clarifying question alongside the products, but NEVER respond with only " +
                        "questions and no products. Show products first, refine later.";

                    ObjectNode agentInput = objectMapper.createObjectNode();
                    agentInput.put("task", enrichedTask);

                    // Execute the agent and format the response.
                    // Use the delegation entry point so specialist "expert help" runs on the fast model.
                    return agentExecutionService.executeAgentForDelegation(agent.getId(), agentInput)
                            .map(result -> formatAgentResponse(agentName, result))
                            .doOnSuccess(response -> log.info("Agent {} completed successfully", agentName))
                            .onErrorResume(e -> {
                                log.error("Agent {} execution failed: {}", agentName, e.getMessage(), e);
                                return createErrorResponse("Agent execution failed: " + e.getMessage());
                            });
                })
                .onErrorResume(e -> {
                    log.error("Error delegating to agent {}: {}", agentName, e.getMessage(), e);
                    return createErrorResponse("Failed to delegate to agent: " + e.getMessage());
                });
    }

    @Override
    public boolean validateInput(JsonNode input) {
        // Both agent_name and task are required
        if (!input.has("agent_name")) {
            log.warn("Agent delegation input missing required 'agent_name' field");
            return false;
        }
        if (!input.has("task")) {
            log.warn("Agent delegation input missing required 'task' field");
            return false;
        }

        // Validate they're not empty
        String agentName = input.get("agent_name").asText();
        String task = input.get("task").asText();

        if (agentName.trim().isEmpty()) {
            log.warn("Agent delegation 'agent_name' cannot be empty");
            return false;
        }
        if (task.trim().isEmpty()) {
            log.warn("Agent delegation 'task' cannot be empty");
            return false;
        }

        return true;
    }

    /**
     * Format the agent execution result into a response for the chatbot
     */
    private JsonNode formatAgentResponse(String agentName, AgentExecutionService.AgentExecutionResult result) {
        try {
            ObjectNode response = objectMapper.createObjectNode();

            // Extract text from agent result
            JsonNode output = result.getOutput();
            String responseText = output.has("text")
                ? output.get("text").asText()
                : output.toString();

            // Build formatted response
            response.put("agent", agentName);
            response.put("response", responseText);
            response.put("tokens_used", result.getInputTokens() + result.getOutputTokens());
            response.put("success", true);

            // Optional: include cost information
            if (result.calculateCost() != null) {
                response.put("cost_usd", result.calculateCost().doubleValue());
            }

            return response;

        } catch (Exception e) {
            log.error("Error formatting agent response: {}", e.getMessage(), e);
            return createErrorResponseNode("Failed to format agent response: " + e.getMessage());
        }
    }

    /**
     * Create an error response as a Mono
     */
    private Mono<JsonNode> createErrorResponse(String errorMessage) {
        return Mono.just(createErrorResponseNode(errorMessage));
    }

    /**
     * Create an error response JsonNode
     */
    private JsonNode createErrorResponseNode(String errorMessage) {
        ObjectNode errorResponse = objectMapper.createObjectNode();
        errorResponse.put("error", errorMessage);
        errorResponse.put("success", false);
        return errorResponse;
    }
}
