package com.shopify.api.handler.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.model.ChatbotConfig;
import com.shopify.api.repository.agent.WorkflowRepository;
import com.shopify.api.service.ChatbotConfigService;
import com.shopify.api.service.tool.WorkflowDelegationToolHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Chat tool handler for delegating tasks to multi-step workflows.
 * Wraps WorkflowDelegationToolHandler for the chat tool registry.
 *
 * Deliberately zero-touch on ChatAgentService: the linked workflows are
 * advertised through this tool's own description, so the storefront chat
 * pipeline is unchanged unless workflows are linked in the chatbot config.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DelegateToWorkflowChatToolHandler implements ChatToolHandler {

    private final WorkflowDelegationToolHandler workflowDelegationHandler;
    private final WorkflowRepository workflowRepository;
    private final ChatbotConfigService chatbotConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getToolName() {
        return "delegate_to_workflow";
    }

    @Override
    public String getToolDescription() {
        StringBuilder description = new StringBuilder(
            "Run an automated multi-step workflow for complex tasks that need several " +
            "processing steps. Use the exact workflow name. Workflows can take up to " +
            "90 seconds, so only delegate when the task genuinely needs one.");
        try {
            List<Long> linkedIds = chatbotConfigService.getConfig().getLinkedWorkflowIds();
            if (linkedIds != null && !linkedIds.isEmpty()) {
                description.append(" Available workflows:");
                for (Long id : linkedIds) {
                    workflowRepository.findById(id)
                        .filter(w -> Boolean.TRUE.equals(w.getIsActive()))
                        .filter(w -> !workflowDelegationHandler.hasApprovalStep(w.getId()))
                        .ifPresent(w -> description.append(" '")
                            .append(w.getName())
                            .append("'")
                            .append(w.getDescription() != null ? " (" + w.getDescription() + ")" : "")
                            .append(";"));
                }
            }
        } catch (Exception e) {
            // Never let description building break tool registration
            log.warn("Could not list linked workflows for tool description: {}", e.getMessage());
        }
        return description.toString();
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode workflowNameProp = objectMapper.createObjectNode();
        workflowNameProp.put("type", "string");
        workflowNameProp.put("description", "Exact name of the workflow to run");
        properties.set("workflow_name", workflowNameProp);

        ObjectNode taskProp = objectMapper.createObjectNode();
        taskProp.put("type", "string");
        taskProp.put("description", "The task or request to send into the workflow");
        properties.set("task", taskProp);

        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("workflow_name");
        required.add("task");
        schema.set("required", required);

        return schema;
    }

    @Override
    public Mono<String> execute(JsonNode input) {
        return workflowDelegationHandler.execute(input)
                .map(JsonNode::toString)
                .onErrorResume(e -> {
                    log.error("Error delegating to workflow: {}", e.getMessage(), e);
                    return Mono.just("{\"error\": \"Workflow delegation failed: "
                        + e.getMessage() + "\", \"success\": false}");
                });
    }

    @Override
    public boolean isEnabled(ChatbotConfig config) {
        return config.getLinkedWorkflowIds() != null && !config.getLinkedWorkflowIds().isEmpty();
    }

    @Override
    public boolean validateInput(JsonNode input) {
        return workflowDelegationHandler.validateInput(input);
    }
}
