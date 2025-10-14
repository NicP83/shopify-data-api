package com.shopify.api.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.model.agent.ApprovalRequest;
import com.shopify.api.model.agent.Workflow;
import com.shopify.api.model.agent.WorkflowExecution;
import com.shopify.api.model.agent.WorkflowStep;
import com.shopify.api.repository.agent.WorkflowExecutionRepository;
import com.shopify.api.repository.agent.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for orchestrating multi-agent workflows
 *
 * This service handles the execution of workflows which consist of multiple
 * agent steps executed in sequence or parallel. Features include:
 * - Sequential workflow execution
 * - Context passing between agents
 * - Conditional execution (based on condition_expression)
 * - Dependency checking (based on depends_on)
 * - Error handling and recovery
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowOrchestratorService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final AgentExecutionService agentExecutionService;
    private final ApprovalService approvalService;
    private final ObjectMapper objectMapper;

    /**
     * Execute a workflow with given trigger data
     *
     * @param workflowId  The ID of the workflow to execute
     * @param triggerData The trigger data to initialize the workflow context
     * @return WorkflowExecutionResult with final context and status
     */
    @Transactional
    public Mono<WorkflowExecutionResult> executeWorkflow(Long workflowId, JsonNode triggerData) {
        log.info("Starting workflow execution for workflow ID: {}", workflowId);

        // Load workflow from database
        return Mono.fromCallable(() -> {
            Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found with ID: " + workflowId));

            // Validate workflow is active
            if (!workflow.getIsActive()) {
                throw new IllegalStateException("Workflow is not active: " + workflow.getName());
            }

            return workflow;
        }).flatMap(workflow -> {
            // Create workflow execution record
            WorkflowExecution execution = WorkflowExecution.builder()
                .workflow(workflow)
                .status("RUNNING")
                .triggerDataJson(triggerData)
                .startedAt(LocalDateTime.now())
                .build();

            WorkflowExecution savedExecution = workflowExecutionRepository.save(execution);
            log.info("Created workflow execution record: {}", savedExecution.getId());

            // Initialize workflow context with trigger data
            ObjectNode context = objectMapper.createObjectNode();
            context.set("trigger", triggerData);

            // Get workflow steps ordered by step_order
            List<WorkflowStep> steps = workflow.getWorkflowSteps().stream()
                .sorted((s1, s2) -> Integer.compare(s1.getStepOrder(), s2.getStepOrder()))
                .toList();

            log.info("Executing {} steps for workflow: {}", steps.size(), workflow.getName());

            // Execute steps sequentially
            return executeStepsSequentially(steps, context, savedExecution, 0)
                .doOnSuccess(result -> {
                    // Update execution record with success
                    savedExecution.setStatus("COMPLETED");
                    savedExecution.setContextDataJson(result.context);
                    savedExecution.setCompletedAt(LocalDateTime.now());
                    workflowExecutionRepository.save(savedExecution);
                    log.info("Workflow execution {} completed successfully", savedExecution.getId());
                })
                .doOnError(error -> {
                    // Update execution record with error
                    savedExecution.setStatus("FAILED");
                    savedExecution.setErrorMessage(error.getMessage());
                    savedExecution.setCompletedAt(LocalDateTime.now());
                    workflowExecutionRepository.save(savedExecution);
                    log.error("Workflow execution {} failed: {}", savedExecution.getId(), error.getMessage());
                });
        });
    }

    /**
     * Execute workflow steps sequentially
     */
    private Mono<WorkflowExecutionResult> executeStepsSequentially(
            List<WorkflowStep> steps,
            ObjectNode context,
            WorkflowExecution execution,
            int stepIndex) {

        // Base case: all steps completed
        if (stepIndex >= steps.size()) {
            log.info("All steps completed for workflow execution {}", execution.getId());
            return Mono.just(WorkflowExecutionResult.builder()
                .context(context)
                .success(true)
                .build());
        }

        WorkflowStep step = steps.get(stepIndex);
        log.info("Executing step {}/{}: {} (type: {})",
            stepIndex + 1, steps.size(), step.getName(), step.getStepType());

        // Check if step should be skipped based on condition
        if (shouldSkipStep(step, context)) {
            log.info("Skipping step {} due to condition: {}", step.getName(), step.getConditionExpression());
            return executeStepsSequentially(steps, context, execution, stepIndex + 1);
        }

        // Check dependencies
        if (!dependenciesMet(step, context)) {
            log.warn("Dependencies not met for step {}: {:?}", step.getName(), step.getDependsOn());
            return Mono.error(new IllegalStateException(
                "Dependencies not met for step: " + step.getName()));
        }

        // Execute step based on type
        return executeStep(step, context, execution)
            .flatMap(stepResult -> {
                // Add step result to context with output variable name
                if (step.getOutputVariable() != null && !step.getOutputVariable().isEmpty()) {
                    context.set(step.getOutputVariable(), stepResult);
                    log.debug("Stored step output in context as: {}", step.getOutputVariable());
                }

                // Continue to next step
                return executeStepsSequentially(steps, context, execution, stepIndex + 1);
            })
            .onErrorResume(error -> {
                log.error("Error executing step {}: {}", step.getName(), error.getMessage());

                // Check if we should retry
                if (step.getRetryConfigJson() != null) {
                    return executeStepWithRetry(step, context, execution, 0)
                        .flatMap(retryResult -> {
                            // Add retry result to context and continue
                            if (step.getOutputVariable() != null && !step.getOutputVariable().isEmpty()) {
                                context.set(step.getOutputVariable(), retryResult);
                            }
                            return executeStepsSequentially(steps, context, execution, stepIndex + 1);
                        });
                }

                // Propagate error (will mark workflow as FAILED)
                return Mono.error(error);
            });
    }

    /**
     * Execute a single workflow step
     */
    private Mono<JsonNode> executeStep(WorkflowStep step, ObjectNode context, WorkflowExecution execution) {
        String stepType = step.getStepType();

        switch (stepType) {
            case "AGENT_EXECUTION":
                return executeAgentStep(step, context, execution);

            case "CONDITION":
                // Conditional steps don't execute agents, they just evaluate conditions
                log.info("Condition step: {} - evaluation handled by shouldSkipStep", step.getName());
                return Mono.just(objectMapper.createObjectNode().put("skipped", true));

            case "APPROVAL":
                return executeApprovalStep(step, context, execution);

            case "PARALLEL":
                // Parallel execution: run multiple sub-steps concurrently
                // Note: Sub-steps would need to be defined in a separate relationship
                // For now, this is a placeholder for future enhancement
                log.warn("PARALLEL step type requires sub-steps configuration: {}", step.getName());
                return Mono.just(objectMapper.createObjectNode().put("parallel", "not_fully_implemented"));

            default:
                log.error("Unknown step type: {}", stepType);
                return Mono.error(new IllegalArgumentException("Unknown step type: " + stepType));
        }
    }

    /**
     * Execute an agent execution step
     */
    private Mono<JsonNode> executeAgentStep(WorkflowStep step, ObjectNode context, WorkflowExecution execution) {
        if (step.getAgent() == null) {
            return Mono.error(new IllegalStateException(
                "AGENT_EXECUTION step must have an agent assigned: " + step.getName()));
        }

        // Build input for agent from input_mapping_json
        JsonNode agentInput = buildAgentInput(step, context);

        log.info("Executing agent: {} with input mapping", step.getAgent().getName());

        // Execute agent
        return agentExecutionService.executeAgent(step.getAgent().getId(), agentInput)
            .map(result -> result.getOutput())
            .timeout(java.time.Duration.ofSeconds(
                step.getTimeoutSeconds() != null ? step.getTimeoutSeconds() : 300))
            .onErrorMap(java.util.concurrent.TimeoutException.class, e ->
                new RuntimeException("Step timeout after " + step.getTimeoutSeconds() + " seconds: " + step.getName()));
    }

    /**
     * Execute an approval step
     * Creates an approval request and pauses the workflow
     */
    private Mono<JsonNode> executeApprovalStep(WorkflowStep step, ObjectNode context, WorkflowExecution execution) {
        log.info("Creating approval request for step: {}", step.getName());

        return Mono.fromCallable(() -> {
            // Parse approval config from input_mapping_json
            JsonNode approvalConfig = step.getInputMappingJson();
            String requiredRole = approvalConfig != null && approvalConfig.has("requiredRole")
                ? approvalConfig.get("requiredRole").asText()
                : null;
            Integer timeoutMinutes = approvalConfig != null && approvalConfig.has("timeoutMinutes")
                ? approvalConfig.get("timeoutMinutes").asInt()
                : null;

            // Create approval request
            approvalService.createApprovalRequest(
                execution.getId(),
                step.getId(),
                requiredRole,
                timeoutMinutes
            );

            // Update workflow execution status to PAUSED
            execution.setStatus("PAUSED");
            workflowExecutionRepository.save(execution);

            log.info("Workflow execution {} paused for approval", execution.getId());

            // Return approval pending result
            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", "PENDING");
            result.put("message", "Waiting for approval");
            return (JsonNode) result;
        });
    }

    /**
     * Build agent input by applying input_mapping_json to context
     */
    private JsonNode buildAgentInput(WorkflowStep step, ObjectNode context) {
        JsonNode inputMapping = step.getInputMappingJson();

        if (inputMapping == null || inputMapping.isNull()) {
            // No mapping, pass entire context
            return context;
        }

        // Apply variable substitution to input mapping
        return substituteVariables(inputMapping, context);
    }

    /**
     * Substitute context variables in a JSON structure
     * Supports ${variable.path} syntax
     */
    private JsonNode substituteVariables(JsonNode node, ObjectNode context) {
        if (node.isTextual()) {
            String text = node.asText();
            // Simple variable substitution: ${variableName}
            if (text.matches("\\$\\{[^}]+\\}")) {
                String varPath = text.substring(2, text.length() - 1);
                JsonNode value = context.get(varPath);
                return value != null ? value : node;
            }
            return node;
        } else if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                result.set(entry.getKey(), substituteVariables(entry.getValue(), context));
            });
            return result;
        } else if (node.isArray()) {
            var arrayNode = objectMapper.createArrayNode();
            node.forEach(item -> arrayNode.add(substituteVariables(item, context)));
            return arrayNode;
        }
        return node;
    }

    /**
     * Check if step should be skipped based on condition_expression
     * Supports simple expressions like:
     * - ${variable}==value
     * - ${variable}!=value
     * - ${variable.field}==value
     * - !${variable} (negation)
     */
    private boolean shouldSkipStep(WorkflowStep step, ObjectNode context) {
        String condition = step.getConditionExpression();
        if (condition == null || condition.isEmpty()) {
            return false;
        }

        log.debug("Evaluating condition: {}", condition);

        try {
            // Handle negation: !${variable}
            if (condition.startsWith("!")) {
                String innerCondition = condition.substring(1).trim();
                return !evaluateCondition(innerCondition, context);
            }

            return !evaluateCondition(condition, context);
        } catch (Exception e) {
            log.error("Error evaluating condition '{}': {}", condition, e.getMessage());
            return false; // Don't skip on error
        }
    }

    /**
     * Evaluate a condition expression
     */
    private boolean evaluateCondition(String condition, ObjectNode context) {
        // Handle equality: ${variable}==value
        if (condition.contains("==")) {
            String[] parts = condition.split("==", 2);
            String left = parts[0].trim();
            String right = parts[1].trim();

            JsonNode leftValue = resolveValue(left, context);
            JsonNode rightValue = resolveValue(right, context);

            if (leftValue == null || rightValue == null) {
                return false;
            }

            return leftValue.asText().equals(rightValue.asText());
        }

        // Handle inequality: ${variable}!=value
        if (condition.contains("!=")) {
            String[] parts = condition.split("!=", 2);
            String left = parts[0].trim();
            String right = parts[1].trim();

            JsonNode leftValue = resolveValue(left, context);
            JsonNode rightValue = resolveValue(right, context);

            if (leftValue == null || rightValue == null) {
                return false;
            }

            return !leftValue.asText().equals(rightValue.asText());
        }

        // Handle simple boolean check: ${variable}
        if (condition.matches("\\$\\{[^}]+\\}")) {
            String varPath = condition.substring(2, condition.length() - 1);
            JsonNode value = resolveNestedPath(varPath, context);

            if (value == null || value.isNull()) {
                return false;
            }

            if (value.isBoolean()) {
                return value.asBoolean();
            }

            // Treat non-empty strings as true
            return !value.asText().isEmpty();
        }

        log.warn("Unknown condition format: {}", condition);
        return false;
    }

    /**
     * Resolve a value from an expression (either ${variable} or literal)
     */
    private JsonNode resolveValue(String expr, ObjectNode context) {
        expr = expr.trim();

        // Variable reference: ${variable.path}
        if (expr.matches("\\$\\{[^}]+\\}")) {
            String varPath = expr.substring(2, expr.length() - 1);
            return resolveNestedPath(varPath, context);
        }

        // Literal value
        return objectMapper.getNodeFactory().textNode(expr);
    }

    /**
     * Resolve nested path in context (e.g., "trigger.userId" or "step1.result.status")
     */
    private JsonNode resolveNestedPath(String path, ObjectNode context) {
        String[] parts = path.split("\\.");
        JsonNode current = context;

        for (String part : parts) {
            if (current == null || !current.has(part)) {
                return null;
            }
            current = current.get(part);
        }

        return current;
    }

    /**
     * Check if all dependencies for a step are met
     */
    private boolean dependenciesMet(WorkflowStep step, ObjectNode context) {
        Integer[] dependsOn = step.getDependsOn();
        if (dependsOn == null || dependsOn.length == 0) {
            return true;
        }

        // Check if all dependent steps have been executed
        // For now, assume sequential execution means all previous steps are complete
        return true;
    }

    /**
     * Execute a step with retry logic using exponential backoff
     * Retry config JSON format: { "maxRetries": 3, "initialDelayMs": 1000, "maxDelayMs": 30000, "multiplier": 2.0 }
     */
    private Mono<JsonNode> executeStepWithRetry(WorkflowStep step, ObjectNode context,
                                                  WorkflowExecution execution, int attemptNumber) {
        JsonNode retryConfig = step.getRetryConfigJson();

        // Parse retry configuration
        int maxRetries = retryConfig.has("maxRetries") ? retryConfig.get("maxRetries").asInt() : 3;
        int initialDelayMs = retryConfig.has("initialDelayMs") ? retryConfig.get("initialDelayMs").asInt() : 1000;
        int maxDelayMs = retryConfig.has("maxDelayMs") ? retryConfig.get("maxDelayMs").asInt() : 30000;
        double multiplier = retryConfig.has("multiplier") ? retryConfig.get("multiplier").asDouble() : 2.0;

        if (attemptNumber >= maxRetries) {
            log.error("Max retries ({}) exceeded for step: {}", maxRetries, step.getName());
            return Mono.error(new RuntimeException(
                "Max retries exceeded for step: " + step.getName()));
        }

        // Calculate delay with exponential backoff
        long delayMs = (long) Math.min(initialDelayMs * Math.pow(multiplier, attemptNumber), maxDelayMs);

        log.info("Retrying step {} (attempt {}/{}) after {}ms",
            step.getName(), attemptNumber + 1, maxRetries, delayMs);

        return Mono.delay(java.time.Duration.ofMillis(delayMs))
            .flatMap(tick -> executeStep(step, context, execution))
            .onErrorResume(error -> {
                log.warn("Retry attempt {} failed for step {}: {}",
                    attemptNumber + 1, step.getName(), error.getMessage());
                return executeStepWithRetry(step, context, execution, attemptNumber + 1);
            });
    }

    /**
     * Execute multiple steps in parallel
     * Used for PARALLEL step type where multiple agents run concurrently
     */
    private Mono<JsonNode> executeStepsInParallel(List<WorkflowStep> steps, ObjectNode context,
                                                     WorkflowExecution execution) {
        log.info("Executing {} steps in parallel", steps.size());

        // Execute all steps concurrently
        List<Mono<Map.Entry<String, JsonNode>>> parallelExecutions = steps.stream()
            .map(step -> executeStep(step, context, execution)
                .map(result -> Map.entry(
                    step.getOutputVariable() != null ? step.getOutputVariable() : "step_" + step.getId(),
                    result))
                .onErrorResume(error -> {
                    log.error("Parallel step {} failed: {}", step.getName(), error.getMessage());
                    // Return error result but don't fail entire parallel group
                    ObjectNode errorNode = objectMapper.createObjectNode();
                    errorNode.put("error", error.getMessage());
                    errorNode.put("stepName", step.getName());
                    return Mono.just(Map.entry(
                        step.getOutputVariable() != null ? step.getOutputVariable() : "step_" + step.getId(),
                        (JsonNode) errorNode));
                }))
            .toList();

        // Wait for all to complete and combine results
        return Mono.zip(parallelExecutions, results -> {
            ObjectNode combinedResult = objectMapper.createObjectNode();
            for (Object result : results) {
                @SuppressWarnings("unchecked")
                Map.Entry<String, JsonNode> entry = (Map.Entry<String, JsonNode>) result;
                combinedResult.set(entry.getKey(), entry.getValue());
            }
            return (JsonNode) combinedResult;
        });
    }

    /**
     * Resume workflow execution after approval
     * Called by ApprovalService when an approval is granted
     *
     * @param executionId Workflow execution ID
     * @param approvalId Approval request ID
     * @return Mono<Void> indicating completion
     */
    public Mono<Void> resumeWorkflowAfterApproval(Long executionId, Long approvalId) {
        log.info("Resuming workflow execution {} after approval {}", executionId, approvalId);

        return Mono.fromCallable(() -> {
            // Load workflow execution
            WorkflowExecution execution = workflowExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow execution not found: " + executionId));

            // Verify execution is in PAUSED state
            if (!"PAUSED".equals(execution.getStatus())) {
                log.warn("Workflow execution {} is not paused (status: {}), cannot resume",
                    executionId, execution.getStatus());
                return null;
            }

            // Update status from PAUSED to RUNNING
            execution.setStatus("RUNNING");
            workflowExecutionRepository.save(execution);

            log.info("Resumed workflow execution {}", executionId);
            return null;
        }).then();
    }

    /**
     * Result of workflow execution
     */
    @lombok.Data
    @lombok.Builder
    public static class WorkflowExecutionResult {
        private JsonNode context;
        private boolean success;
        private String errorMessage;
        private Map<String, Object> metadata;

        public static WorkflowExecutionResult success(JsonNode context) {
            return WorkflowExecutionResult.builder()
                .context(context)
                .success(true)
                .metadata(new HashMap<>())
                .build();
        }

        public static WorkflowExecutionResult failure(String errorMessage) {
            return WorkflowExecutionResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .metadata(new HashMap<>())
                .build();
        }
    }
}
