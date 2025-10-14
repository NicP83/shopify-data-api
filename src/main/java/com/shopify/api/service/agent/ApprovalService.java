package com.shopify.api.service.agent;

import com.shopify.api.model.agent.ApprovalRequest;
import com.shopify.api.model.agent.WorkflowExecution;
import com.shopify.api.model.agent.WorkflowStep;
import com.shopify.api.repository.agent.ApprovalRequestRepository;
import com.shopify.api.repository.agent.WorkflowExecutionRepository;
import com.shopify.api.repository.agent.WorkflowStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing human-in-the-loop approval requests
 *
 * Handles creation, approval, rejection, and timeout of approval requests
 * during workflow execution. Supports role-based approvals.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 * See: docs/multi-agent/IMPLEMENTATION_ROADMAP.md Phase 8
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final WorkflowStepRepository workflowStepRepository;
    private final WorkflowOrchestratorService workflowOrchestratorService;

    /**
     * Create a new approval request
     *
     * @param executionId Workflow execution ID
     * @param stepId Workflow step ID
     * @param requiredRole Optional role required for approval
     * @param timeoutMinutes Optional timeout in minutes
     * @return Created approval request
     */
    @Transactional
    public ApprovalRequest createApprovalRequest(
            Long executionId,
            Long stepId,
            String requiredRole,
            Integer timeoutMinutes) {

        log.info("Creating approval request for execution {} step {}", executionId, stepId);

        WorkflowExecution execution = workflowExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow execution not found: " + executionId));

        WorkflowStep step = workflowStepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow step not found: " + stepId));

        // Calculate timeout if specified
        LocalDateTime timeoutAt = null;
        if (timeoutMinutes != null && timeoutMinutes > 0) {
            timeoutAt = LocalDateTime.now().plusMinutes(timeoutMinutes);
        }

        ApprovalRequest request = ApprovalRequest.builder()
                .workflowExecution(execution)
                .workflowStep(step)
                .status("PENDING")
                .requiredRole(requiredRole)
                .timeoutAt(timeoutAt)
                .build();

        ApprovalRequest saved = approvalRequestRepository.save(request);
        log.info("Created approval request {}", saved.getId());

        return saved;
    }

    /**
     * Approve an approval request
     *
     * @param requestId Approval request ID
     * @param approvedBy User or system that approved
     * @param comments Optional approval comments
     * @return Updated approval request
     */
    @Transactional
    public Mono<ApprovalRequest> approveRequest(Long requestId, String approvedBy, String comments) {
        log.info("Approving request {} by {}", requestId, approvedBy);

        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Approval request not found: " + requestId));

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Cannot approve request with status: " + request.getStatus());
        }

        request.setStatus("APPROVED");
        request.setApprovedBy(approvedBy);
        request.setApprovedAt(LocalDateTime.now());
        request.setComments(comments);

        ApprovalRequest saved = approvalRequestRepository.save(request);
        log.info("Approved request {}", saved.getId());

        // Resume workflow execution
        return resumeWorkflowAfterApproval(saved);
    }

    /**
     * Reject an approval request
     *
     * @param requestId Approval request ID
     * @param rejectedBy User or system that rejected
     * @param reason Rejection reason
     * @return Updated approval request
     */
    @Transactional
    public Mono<ApprovalRequest> rejectRequest(Long requestId, String rejectedBy, String reason) {
        log.info("Rejecting request {} by {}", requestId, rejectedBy);

        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Approval request not found: " + requestId));

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Cannot reject request with status: " + request.getStatus());
        }

        request.setStatus("REJECTED");
        request.setApprovedBy(rejectedBy);
        request.setApprovedAt(LocalDateTime.now());
        request.setComments(reason);

        ApprovalRequest saved = approvalRequestRepository.save(request);
        log.info("Rejected request {}", saved.getId());

        // Fail the workflow execution
        return failWorkflowAfterRejection(saved);
    }

    /**
     * Get all pending approval requests
     *
     * @return List of pending requests
     */
    public List<ApprovalRequest> getPendingApprovals() {
        return approvalRequestRepository.findByStatusOrderByRequestedAtAsc("PENDING");
    }

    /**
     * Get pending approvals for a specific role
     *
     * @param role Required role
     * @return List of pending requests
     */
    public List<ApprovalRequest> getPendingApprovalsByRole(String role) {
        return approvalRequestRepository.findByRequiredRoleAndStatus(role, "PENDING");
    }

    /**
     * Get approval requests for a workflow execution
     *
     * @param executionId Workflow execution ID
     * @return List of approval requests
     */
    public List<ApprovalRequest> getApprovalsByExecution(Long executionId) {
        return approvalRequestRepository.findByWorkflowExecutionId(executionId);
    }

    /**
     * Get count of pending approvals
     *
     * @return Count of pending requests
     */
    public long getPendingApprovalCount() {
        return approvalRequestRepository.countByStatus("PENDING");
    }

    /**
     * Process timed out approval requests
     *
     * Called periodically to handle approvals that have exceeded their timeout
     */
    @Transactional
    public void processTimeouts() {
        List<ApprovalRequest> timedOut = approvalRequestRepository.findTimedOutRequests(LocalDateTime.now());

        if (!timedOut.isEmpty()) {
            log.info("Processing {} timed out approval requests", timedOut.size());

            for (ApprovalRequest request : timedOut) {
                request.setStatus("TIMEOUT");
                request.setComments("Approval request timed out");
                approvalRequestRepository.save(request);

                // Fail the workflow due to timeout
                failWorkflowAfterRejection(request).subscribe();
            }
        }
    }

    /**
     * Resume workflow execution after approval
     */
    private Mono<ApprovalRequest> resumeWorkflowAfterApproval(ApprovalRequest request) {
        Long executionId = request.getWorkflowExecution().getId();

        return workflowOrchestratorService.resumeWorkflowAfterApproval(executionId, request.getId())
                .thenReturn(request)
                .doOnSuccess(r -> log.info("Resumed workflow {} after approval", executionId))
                .doOnError(e -> log.error("Failed to resume workflow {} after approval", executionId, e));
    }

    /**
     * Fail workflow execution after rejection
     */
    private Mono<ApprovalRequest> failWorkflowAfterRejection(ApprovalRequest request) {
        Long executionId = request.getWorkflowExecution().getId();
        String reason = request.getComments() != null ? request.getComments() : "Approval rejected";

        WorkflowExecution execution = workflowExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow execution not found: " + executionId));

        execution.setStatus("FAILED");
        execution.setCompletedAt(LocalDateTime.now());
        execution.setErrorMessage("Approval rejected: " + reason);
        workflowExecutionRepository.save(execution);

        log.info("Failed workflow {} due to approval rejection", executionId);
        return Mono.just(request);
    }
}
