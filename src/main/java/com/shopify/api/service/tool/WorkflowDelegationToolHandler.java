package com.shopify.api.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.model.agent.Workflow;
import com.shopify.api.repository.agent.WorkflowRepository;
import com.shopify.api.service.ChatbotConfigService;
import com.shopify.api.service.agent.WorkflowOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Tool handler for delegating tasks to multi-step workflows from the chatbot.
 *
 * Mirrors AgentDelegationToolHandler but runs a whole workflow instead of a
 * single agent. Guard rails:
 * - only workflows explicitly linked in ChatbotConfig.linkedWorkflowIds are
 *   runnable (Claude cannot invoke arbitrary DB workflows by name)
 * - workflows containing APPROVAL steps are refused (a customer chat must not
 *   create dangling PAUSED executions waiting on human approval)
 * - hard 90s timeout, under the 120s MVC async request timeout
 *
 * Input schema: { "workflow_name": "...", "task": "..." }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowDelegationToolHandler implements ToolHandler {

    private static final Duration WORKFLOW_TIMEOUT = Duration.ofSeconds(90);
    private static final int MAX_RESULT_CHARS = 6000;

    private final WorkflowRepository workflowRepository;
    private final WorkflowOrchestratorService workflowOrchestratorService;
    private final ChatbotConfigService chatbotConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<JsonNode> execute(JsonNode input) {
        String workflowName = input.get("workflow_name").asText();
        String task = input.get("task").asText();

        log.info("=== DELEGATING TO WORKFLOW ===");
        log.info("Workflow: '{}', Task: '{}'", workflowName, task);

        return Mono.fromCallable(() -> workflowRepository.findByName(workflowName))
            .flatMap(workflowOpt -> {
                if (workflowOpt.isEmpty()) {
                    return createErrorResponse("Workflow '" + workflowName + "' not found");
                }

                Workflow workflow = workflowOpt.get();

                if (!Boolean.TRUE.equals(workflow.getIsActive())) {
                    return createErrorResponse("Workflow '" + workflowName + "' is not active");
                }

                // Security gate: only workflows explicitly linked to the chatbot
                List<Long> linkedIds = chatbotConfigService.getConfig().getLinkedWorkflowIds();
                if (linkedIds == null || !linkedIds.contains(workflow.getId())) {
                    log.warn("Workflow {} is not linked to the chatbot - refusing", workflow.getId());
                    return createErrorResponse(
                        "Workflow '" + workflowName + "' is not available from chat");
                }

                if (hasApprovalStep(workflow.getId())) {
                    return createErrorResponse(
                        "Workflow '" + workflowName + "' requires human approval and cannot be " +
                        "run from chat. Suggest the customer contacts staff instead.");
                }

                // Both "task" and "message" keys so ${trigger.message}-style
                // input mappings in existing workflows resolve
                ObjectNode triggerData = objectMapper.createObjectNode();
                triggerData.put("task", task);
                triggerData.put("message", task);
                triggerData.put("source", "chatbot");

                return workflowOrchestratorService.executeWorkflow(workflow.getId(), triggerData)
                    .timeout(WORKFLOW_TIMEOUT)
                    .map(result -> formatWorkflowResponse(workflowName, result))
                    .onErrorResume(TimeoutException.class, e -> {
                        log.warn("Workflow {} timed out after {}s from chat", workflowName,
                            WORKFLOW_TIMEOUT.toSeconds());
                        ObjectNode timeout = objectMapper.createObjectNode();
                        timeout.put("success", false);
                        timeout.put("status", "timeout");
                        timeout.put("error", "Workflow is taking too long; it may still complete " +
                            "in the background but results will not appear in this chat.");
                        return Mono.just(timeout);
                    })
                    .onErrorResume(e -> {
                        log.error("Workflow {} execution failed: {}", workflowName, e.getMessage(), e);
                        return createErrorResponse("Workflow execution failed: " + e.getMessage());
                    });
            })
            .onErrorResume(e -> {
                log.error("Error delegating to workflow {}: {}", workflowName, e.getMessage(), e);
                return createErrorResponse("Failed to delegate to workflow: " + e.getMessage());
            });
    }

    @Override
    public boolean validateInput(JsonNode input) {
        if (!input.has("workflow_name") || input.get("workflow_name").asText().trim().isEmpty()) {
            log.warn("Workflow delegation input missing 'workflow_name'");
            return false;
        }
        if (!input.has("task") || input.get("task").asText().trim().isEmpty()) {
            log.warn("Workflow delegation input missing 'task'");
            return false;
        }
        return true;
    }

    /**
     * True when any step of the workflow is an APPROVAL step
     */
    public boolean hasApprovalStep(Long workflowId) {
        return workflowRepository.findByIdWithStepsAndAgents(workflowId)
            .map(w -> w.getWorkflowSteps().stream()
                .anyMatch(s -> "APPROVAL".equals(s.getStepType())))
            .orElse(false);
    }

    private JsonNode formatWorkflowResponse(String workflowName,
                                            WorkflowOrchestratorService.WorkflowExecutionResult result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("workflow", workflowName);
        response.put("success", result.isSuccess());

        if (!result.isSuccess()) {
            response.put("error", result.getErrorMessage() != null
                ? result.getErrorMessage() : "Workflow failed");
            return response;
        }

        // Workflow contexts accumulate every step output - cap the payload so
        // it does not blow the chat token budget
        String context = result.getContext() != null ? result.getContext().toString() : "{}";
        if (context.length() > MAX_RESULT_CHARS) {
            response.put("result", context.substring(0, MAX_RESULT_CHARS));
            response.put("truncated", true);
        } else {
            response.put("result", context);
        }
        return response;
    }

    private Mono<JsonNode> createErrorResponse(String errorMessage) {
        ObjectNode errorResponse = objectMapper.createObjectNode();
        errorResponse.put("error", errorMessage);
        errorResponse.put("success", false);
        return Mono.just(errorResponse);
    }
}
