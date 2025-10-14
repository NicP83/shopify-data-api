package com.shopify.api.controller.agent;

import com.shopify.api.model.agent.ApprovalRequest;
import com.shopify.api.service.agent.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * REST API for managing approval requests
 *
 * Provides endpoints for creating, approving, and rejecting approval requests
 * during workflow execution.
 *
 * See: docs/multi-agent/IMPLEMENTATION_ROADMAP.md Phase 8
 */
@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ApprovalController {

    private final ApprovalService approvalService;

    /**
     * Get all pending approval requests
     *
     * GET /api/approvals/pending
     */
    @GetMapping("/pending")
    public ResponseEntity<List<ApprovalRequest>> getPendingApprovals(
            @RequestParam(required = false) String role) {

        log.info("Getting pending approvals" + (role != null ? " for role: " + role : ""));

        List<ApprovalRequest> approvals = role != null
                ? approvalService.getPendingApprovalsByRole(role)
                : approvalService.getPendingApprovals();

        return ResponseEntity.ok(approvals);
    }

    /**
     * Get approval requests for a workflow execution
     *
     * GET /api/approvals/execution/{executionId}
     */
    @GetMapping("/execution/{executionId}")
    public ResponseEntity<List<ApprovalRequest>> getApprovalsByExecution(
            @PathVariable Long executionId) {

        log.info("Getting approvals for execution {}", executionId);
        List<ApprovalRequest> approvals = approvalService.getApprovalsByExecution(executionId);
        return ResponseEntity.ok(approvals);
    }

    /**
     * Get count of pending approvals
     *
     * GET /api/approvals/count
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getPendingCount() {
        long count = approvalService.getPendingApprovalCount();
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Approve an approval request
     *
     * POST /api/approvals/{id}/approve
     *
     * Request body:
     * {
     *   "approvedBy": "user@example.com",
     *   "comments": "Looks good"
     * }
     */
    @PostMapping("/{id}/approve")
    public Mono<ResponseEntity<ApprovalRequest>> approveRequest(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        String approvedBy = request.get("approvedBy");
        String comments = request.get("comments");

        log.info("Approving request {} by {}", id, approvedBy);

        return approvalService.approveRequest(id, approvedBy, comments)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error approving request {}", id, e);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    /**
     * Reject an approval request
     *
     * POST /api/approvals/{id}/reject
     *
     * Request body:
     * {
     *   "rejectedBy": "user@example.com",
     *   "reason": "Does not meet requirements"
     * }
     */
    @PostMapping("/{id}/reject")
    public Mono<ResponseEntity<ApprovalRequest>> rejectRequest(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        String rejectedBy = request.get("rejectedBy");
        String reason = request.get("reason");

        log.info("Rejecting request {} by {}", id, rejectedBy);

        return approvalService.rejectRequest(id, rejectedBy, reason)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error rejecting request {}", id, e);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    /**
     * Create a new approval request (typically called by orchestrator)
     *
     * POST /api/approvals
     *
     * Request body:
     * {
     *   "executionId": 123,
     *   "stepId": 456,
     *   "requiredRole": "manager",
     *   "timeoutMinutes": 60
     * }
     */
    @PostMapping
    public ResponseEntity<ApprovalRequest> createApprovalRequest(
            @RequestBody Map<String, Object> request) {

        Long executionId = ((Number) request.get("executionId")).longValue();
        Long stepId = ((Number) request.get("stepId")).longValue();
        String requiredRole = (String) request.get("requiredRole");
        Integer timeoutMinutes = request.get("timeoutMinutes") != null
                ? ((Number) request.get("timeoutMinutes")).intValue()
                : null;

        log.info("Creating approval request for execution {} step {}", executionId, stepId);

        try {
            ApprovalRequest approval = approvalService.createApprovalRequest(
                    executionId, stepId, requiredRole, timeoutMinutes);
            return ResponseEntity.ok(approval);
        } catch (Exception e) {
            log.error("Error creating approval request", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
