package com.shopify.api.controller.agent;

import com.shopify.api.dto.agent.*;
import com.shopify.api.model.agent.Workflow;
import com.shopify.api.model.agent.WorkflowStep;
import com.shopify.api.service.agent.WorkflowOrchestratorService;
import com.shopify.api.service.agent.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;

/**
 * REST Controller for Workflow and WorkflowStep management
 *
 * Provides endpoints for creating and managing workflows and their steps.
 * Workflows orchestrate multiple agents in sequential or parallel execution.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 */
@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowOrchestratorService workflowOrchestratorService;

    /**
     * Create a new workflow
     * POST /api/workflows
     */
    @PostMapping
    public ResponseEntity<WorkflowResponse> createWorkflow(@Valid @RequestBody CreateWorkflowRequest request) {
        log.info("Creating new workflow: {}", request.getName());

        Workflow workflow = Workflow.builder()
            .name(request.getName())
            .description(request.getDescription())
            .triggerType(request.getTriggerType())
            .triggerConfigJson(request.getTriggerConfigJson())
            .executionMode(request.getExecutionMode())
            .isActive(request.getIsActive())
            .inputSchemaJson(request.getInputSchemaJson())
            .interfaceType(request.getInterfaceType())
            .isPublic(request.getIsPublic())
            .build();

        Workflow createdWorkflow = workflowService.createWorkflow(workflow);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(WorkflowResponse.fromEntity(createdWorkflow));
    }

    /**
     * Get all workflows
     * GET /api/workflows
     */
    @GetMapping
    public ResponseEntity<List<WorkflowResponse>> getAllWorkflows(
        @RequestParam(required = false) Boolean activeOnly,
        @RequestParam(required = false) String triggerType
    ) {
        log.info("Fetching workflows - activeOnly: {}, triggerType: {}", activeOnly, triggerType);

        List<Workflow> workflows;

        if (triggerType != null) {
            workflows = Boolean.TRUE.equals(activeOnly)
                ? workflowService.getActiveWorkflowsByTriggerType(triggerType)
                : workflowService.getWorkflowsByTriggerType(triggerType);
        } else if (Boolean.TRUE.equals(activeOnly)) {
            workflows = workflowService.getActiveWorkflows();
        } else {
            workflows = workflowService.getAllWorkflows();
        }

        List<WorkflowResponse> response = workflows.stream()
            .map(WorkflowResponse::fromEntity)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get workflow by ID
     * GET /api/workflows/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<WorkflowResponse> getWorkflowById(@PathVariable Long id) {
        log.info("Fetching workflow with ID: {}", id);

        return workflowService.getWorkflowById(id)
            .map(WorkflowResponse::fromEntity)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update an existing workflow
     * PUT /api/workflows/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<WorkflowResponse> updateWorkflow(
        @PathVariable Long id,
        @Valid @RequestBody CreateWorkflowRequest request
    ) {
        log.info("Updating workflow with ID: {}", id);

        Workflow updatedWorkflow = Workflow.builder()
            .name(request.getName())
            .description(request.getDescription())
            .triggerType(request.getTriggerType())
            .triggerConfigJson(request.getTriggerConfigJson())
            .executionMode(request.getExecutionMode())
            .isActive(request.getIsActive())
            .inputSchemaJson(request.getInputSchemaJson())
            .interfaceType(request.getInterfaceType())
            .isPublic(request.getIsPublic())
            .build();

        Workflow workflow = workflowService.updateWorkflow(id, updatedWorkflow);
        return ResponseEntity.ok(WorkflowResponse.fromEntity(workflow));
    }

    /**
     * Delete a workflow
     * DELETE /api/workflows/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable Long id) {
        log.info("Deleting workflow with ID: {}", id);
        workflowService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Activate a workflow
     * POST /api/workflows/{id}/activate
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activateWorkflow(@PathVariable Long id) {
        log.info("Activating workflow with ID: {}", id);
        workflowService.activateWorkflow(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Deactivate a workflow
     * POST /api/workflows/{id}/deactivate
     */
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateWorkflow(@PathVariable Long id) {
        log.info("Deactivating workflow with ID: {}", id);
        workflowService.deactivateWorkflow(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Add a step to a workflow
     * POST /api/workflows/{workflowId}/steps
     */
    @PostMapping("/{workflowId}/steps")
    public ResponseEntity<WorkflowStepResponse> addWorkflowStep(
        @PathVariable Long workflowId,
        @Valid @RequestBody CreateWorkflowStepRequest request
    ) {
        log.info("Adding step to workflow {}: {}", workflowId, request.getName());

        WorkflowStep step = WorkflowStep.builder()
            .stepOrder(request.getStepOrder())
            .stepType(request.getStepType())
            .name(request.getName())
            .inputMappingJson(request.getInputMappingJson())
            .outputVariable(request.getOutputVariable())
            .conditionExpression(request.getConditionExpression())
            .dependsOn(request.getDependsOn())
            .approvalConfigJson(request.getApprovalConfigJson())
            .retryConfigJson(request.getRetryConfigJson())
            .timeoutSeconds(request.getTimeoutSeconds())
            .build();

        WorkflowStep createdStep = workflowService.addWorkflowStep(workflowId, request.getAgentId(), step);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(WorkflowStepResponse.fromEntity(createdStep));
    }

    /**
     * Get all steps for a workflow
     * GET /api/workflows/{workflowId}/steps
     */
    @GetMapping("/{workflowId}/steps")
    public ResponseEntity<List<WorkflowStepResponse>> getWorkflowSteps(@PathVariable Long workflowId) {
        log.info("Fetching steps for workflow {}", workflowId);

        List<WorkflowStep> steps = workflowService.getWorkflowSteps(workflowId);
        List<WorkflowStepResponse> response = steps.stream()
            .map(WorkflowStepResponse::fromEntity)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Update a workflow step
     * PUT /api/workflows/{workflowId}/steps/{stepId}
     */
    @PutMapping("/{workflowId}/steps/{stepId}")
    public ResponseEntity<WorkflowStepResponse> updateWorkflowStep(
        @PathVariable Long workflowId,
        @PathVariable Long stepId,
        @Valid @RequestBody CreateWorkflowStepRequest request
    ) {
        log.info("Updating step {} in workflow {}", stepId, workflowId);

        WorkflowStep updatedStep = WorkflowStep.builder()
            .stepOrder(request.getStepOrder())
            .stepType(request.getStepType())
            .name(request.getName())
            .inputMappingJson(request.getInputMappingJson())
            .outputVariable(request.getOutputVariable())
            .conditionExpression(request.getConditionExpression())
            .dependsOn(request.getDependsOn())
            .approvalConfigJson(request.getApprovalConfigJson())
            .retryConfigJson(request.getRetryConfigJson())
            .timeoutSeconds(request.getTimeoutSeconds())
            .build();

        WorkflowStep step = workflowService.updateWorkflowStep(stepId, request.getAgentId(), updatedStep);
        return ResponseEntity.ok(WorkflowStepResponse.fromEntity(step));
    }

    /**
     * Delete a workflow step
     * DELETE /api/workflows/{workflowId}/steps/{stepId}
     */
    @DeleteMapping("/{workflowId}/steps/{stepId}")
    public ResponseEntity<Void> deleteWorkflowStep(
        @PathVariable Long workflowId,
        @PathVariable Long stepId
    ) {
        log.info("Deleting step {} from workflow {}", stepId, workflowId);
        workflowService.deleteWorkflowStep(stepId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reorder workflow steps
     * POST /api/workflows/{workflowId}/steps/reorder
     * Body: List of step IDs in the desired order
     */
    @PostMapping("/{workflowId}/steps/reorder")
    public ResponseEntity<Void> reorderWorkflowSteps(
        @PathVariable Long workflowId,
        @RequestBody List<Long> stepIds
    ) {
        log.info("Reordering steps for workflow {}", workflowId);
        workflowService.reorderWorkflowSteps(workflowId, stepIds);
        return ResponseEntity.ok().build();
    }

    /**
     * Execute a workflow
     * POST /api/workflows/{id}/execute
     */
    @PostMapping("/{id}/execute")
    public Mono<ResponseEntity<Object>> executeWorkflow(
        @PathVariable Long id,
        @RequestBody(required = false) com.fasterxml.jackson.databind.JsonNode triggerData
    ) {
        log.info("Executing workflow ID: {}", id);

        // Default to empty object if no trigger data provided
        if (triggerData == null) {
            triggerData = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        }

        return workflowOrchestratorService.executeWorkflow(id, triggerData)
            .map(result -> {
                // Build response
                java.util.Map<String, Object> response = new java.util.HashMap<>();
                response.put("success", result.isSuccess());
                response.put("context", result.getContext());
                if (result.getErrorMessage() != null) {
                    response.put("error", result.getErrorMessage());
                }
                return ResponseEntity.ok((Object) response);
            })
            .onErrorResume(error -> {
                log.error("Workflow execution error: {}", error.getMessage());
                java.util.Map<String, Object> errorResponse = new java.util.HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", error.getMessage());
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body((Object) errorResponse));
            });
    }

    /**
     * Execute a public workflow (no authentication required)
     * POST /api/workflows/public/{id}/execute
     */
    @PostMapping("/public/{id}/execute")
    public Mono<ResponseEntity<Object>> executePublicWorkflow(
        @PathVariable Long id,
        @RequestBody(required = false) com.fasterxml.jackson.databind.JsonNode input
    ) {
        log.info("Public execution request for workflow ID: {}", id);

        // Verify workflow exists and is public
        return Mono.fromCallable(() -> workflowService.getWorkflowById(id))
            .flatMap(optWorkflow -> {
                if (optWorkflow.isEmpty()) {
                    return Mono.just(ResponseEntity.notFound().build());
                }

                Workflow workflow = optWorkflow.get();
                if (!Boolean.TRUE.equals(workflow.getIsPublic())) {
                    java.util.Map<String, Object> errorResponse = new java.util.HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", "Workflow is not public");
                    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body((Object) errorResponse));
                }

                // Default to empty object if no input provided
                com.fasterxml.jackson.databind.JsonNode inputData = input != null
                    ? input
                    : new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();

                return workflowOrchestratorService.executeWorkflow(id, inputData)
                    .map(result -> {
                        java.util.Map<String, Object> response = new java.util.HashMap<>();
                        response.put("success", result.isSuccess());
                        response.put("context", result.getContext());
                        if (result.getErrorMessage() != null) {
                            response.put("error", result.getErrorMessage());
                        }
                        return ResponseEntity.ok((Object) response);
                    });
            })
            .onErrorResume(error -> {
                log.error("Public workflow execution error: {}", error.getMessage());
                java.util.Map<String, Object> errorResponse = new java.util.HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", error.getMessage());
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body((Object) errorResponse));
            });
    }

    /**
     * Exception handler for IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        log.error("Validation error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
