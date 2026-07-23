package com.shopify.api.controller.agent;

import com.shopify.api.dto.agent.WorkflowExecutionResponse;
import com.shopify.api.repository.agent.WorkflowExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for workflow execution history.
 *
 * Serves the WorkflowExecutions admin page. Method-level absolute paths
 * because it spans two path roots (/api/executions and /api/workflows).
 *
 * See: docs/multi-agent/API_SPECIFICATION.md
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ExecutionController {

    private static final int MAX_EXECUTIONS = 200;

    private final WorkflowExecutionRepository workflowExecutionRepository;

    /**
     * Get recent workflow executions across all workflows (newest first, capped)
     * GET /api/executions
     */
    @GetMapping("/api/executions")
    public ResponseEntity<List<WorkflowExecutionResponse>> getAllExecutions() {
        List<WorkflowExecutionResponse> response = workflowExecutionRepository
            .findAllWithWorkflow(PageRequest.of(0, MAX_EXECUTIONS))
            .stream()
            .map(WorkflowExecutionResponse::fromEntity)
            .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Get a single workflow execution
     * GET /api/executions/{id}
     */
    @GetMapping("/api/executions/{id}")
    public ResponseEntity<WorkflowExecutionResponse> getExecution(@PathVariable Long id) {
        return workflowExecutionRepository.findByIdWithWorkflow(id)
            .map(execution -> ResponseEntity.ok(WorkflowExecutionResponse.fromEntity(execution)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get executions for a specific workflow (newest first)
     * GET /api/workflows/{workflowId}/executions
     */
    @GetMapping("/api/workflows/{workflowId}/executions")
    public ResponseEntity<List<WorkflowExecutionResponse>> getWorkflowExecutions(
        @PathVariable Long workflowId
    ) {
        List<WorkflowExecutionResponse> response = workflowExecutionRepository
            .findByWorkflowIdWithWorkflow(workflowId)
            .stream()
            .map(WorkflowExecutionResponse::fromEntity)
            .toList();
        return ResponseEntity.ok(response);
    }
}
