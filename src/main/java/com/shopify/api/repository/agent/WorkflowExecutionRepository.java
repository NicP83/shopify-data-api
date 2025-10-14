package com.shopify.api.repository.agent;

import com.shopify.api.model.agent.WorkflowExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for WorkflowExecution entity
 *
 * Tracks workflow execution runs with full context and status.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 */
@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, Long> {

    /**
     * Find executions by workflow ID
     */
    List<WorkflowExecution> findByWorkflowId(Long workflowId);

    /**
     * Find executions by status
     */
    List<WorkflowExecution> findByStatus(String status);

    /**
     * Find executions by workflow ID and status
     */
    List<WorkflowExecution> findByWorkflowIdAndStatus(Long workflowId, String status);

    /**
     * Find recent executions for a workflow
     */
    List<WorkflowExecution> findTop10ByWorkflowIdOrderByCreatedAtDesc(Long workflowId);

    /**
     * Find executions created within a time range
     */
    List<WorkflowExecution> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find currently running executions
     */
    @Query("SELECT we FROM WorkflowExecution we WHERE we.status IN ('PENDING', 'RUNNING')")
    List<WorkflowExecution> findActiveExecutions();

    /**
     * Count executions by status
     */
    long countByStatus(String status);

    /**
     * Count executions for a workflow
     */
    long countByWorkflowId(Long workflowId);
}
