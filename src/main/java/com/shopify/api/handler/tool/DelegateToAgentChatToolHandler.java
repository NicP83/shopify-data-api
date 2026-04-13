package com.shopify.api.handler.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.model.ChatbotConfig;
import com.shopify.api.service.tool.AgentDelegationToolHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Chat tool handler for delegating tasks to specialist agents.
 * Wraps the existing AgentDelegationToolHandler for the chat tool registry.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DelegateToAgentChatToolHandler implements ChatToolHandler {

    private final AgentDelegationToolHandler agentDelegationHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getToolName() {
        return "delegate_to_agent";
    }

    @Override
    public String getToolDescription() {
        return "Delegate a task to a specialist agent. " +
                "Use this when you need expert knowledge or specialized capabilities. " +
                "The agent will process the task and return results.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode agentNameProp = objectMapper.createObjectNode();
        agentNameProp.put("type", "string");
        agentNameProp.put("description", "Name of the specialist agent to delegate to");
        properties.set("agent_name", agentNameProp);

        ObjectNode taskProp = objectMapper.createObjectNode();
        taskProp.put("type", "string");
        taskProp.put("description", "The task or question to send to the specialist agent");
        properties.set("task", taskProp);

        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("agent_name");
        required.add("task");
        schema.set("required", required);

        return schema;
    }

    @Override
    public Mono<String> execute(JsonNode input) {
        return agentDelegationHandler.execute(input)
                .map(JsonNode::toString)
                .onErrorResume(e -> {
                    log.error("Error delegating to agent: {}", e.getMessage(), e);
                    return Mono.just("{\"error\": \"Delegation failed: " + e.getMessage() + "\"}");
                });
    }

    @Override
    public boolean isEnabled(ChatbotConfig config) {
        return config.getLinkedAgentIds() != null && !config.getLinkedAgentIds().isEmpty();
    }

    @Override
    public boolean validateInput(JsonNode input) {
        return agentDelegationHandler.validateInput(input);
    }
}
