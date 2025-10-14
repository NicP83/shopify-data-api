package com.shopify.api.repository.agent;

import com.shopify.api.model.agent.WorkflowStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for WorkflowStep entity
 *
 * Manages workflow steps with ordering and dependencies.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 */
@Repository
public interface WorkflowStepRepository extends JpaRepository<WorkflowStep, Long> {

    /**
     * Find all steps for a workflow, ordered by step_order
     */
    List<WorkflowStep> findByWorkflowIdOrderByStepOrderAsc(Long workflowId);

    /**
     * Find steps by workflow and step type
     */
    List<WorkflowStep> findByWorkflowIdAndStepType(Long workflowId, String stepType);

    /**
     * Find steps using a specific agent
     */
    List<WorkflowStep> findByAgentId(Long agentId);

    /**
     * Delete all steps for a workflow
     */
    void deleteByWorkflowId(Long workflowId);
}
