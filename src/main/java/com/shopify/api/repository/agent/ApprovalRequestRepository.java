package com.shopify.api.repository.agent;

import com.shopify.api.model.agent.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for ApprovalRequest entity
 *
 * Manages human-in-the-loop approval tracking with timeout support.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 */
@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    /**
     * Find approval requests by workflow execution
     */
    List<ApprovalRequest> findByWorkflowExecutionId(Long workflowExecutionId);

    /**
     * Find approval requests by workflow step
     */
    List<ApprovalRequest> findByWorkflowStepId(Long workflowStepId);

    /**
     * Find approval requests by status
     */
    List<ApprovalRequest> findByStatus(String status);

    /**
     * Find pending approval requests
     */
    List<ApprovalRequest> findByStatusOrderByRequestedAtAsc(String status);

    /**
     * Find approval requests by required role
     */
    List<ApprovalRequest> findByRequiredRoleAndStatus(String requiredRole, String status);

    /**
     * Find timed out approval requests
     */
    @Query("SELECT ar FROM ApprovalRequest ar WHERE ar.status = 'PENDING' AND ar.timeoutAt < :now")
    List<ApprovalRequest> findTimedOutRequests(LocalDateTime now);

    /**
     * Find approvals by approver
     */
    List<ApprovalRequest> findByApprovedBy(String approvedBy);

    /**
     * Count pending approvals
     */
    long countByStatus(String status);

    /**
     * Count pending approvals for a role
     */
    long countByRequiredRoleAndStatus(String requiredRole, String status);
}
