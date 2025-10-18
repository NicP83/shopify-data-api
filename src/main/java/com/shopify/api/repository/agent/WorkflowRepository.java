package com.shopify.api.repository.agent;

import com.shopify.api.model.agent.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Workflow entity
 *
 * Provides database access methods for Workflow operations.
 * Workflows orchestrate multiple agents in sequences.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 */
@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, Long> {

    /**
     * Find a workflow by name
     */
    Optional<Workflow> findByName(String name);

    /**
     * Find all active workflows
     */
    List<Workflow> findByIsActiveTrue();

    /**
     * Find workflows by trigger type
     */
    List<Workflow> findByTriggerType(String triggerType);

    /**
     * Find active workflows by trigger type
     */
    List<Workflow> findByTriggerTypeAndIsActiveTrue(String triggerType);

    /**
     * Find workflows by execution mode
     */
    List<Workflow> findByExecutionMode(String executionMode);

    /**
     * Check if a workflow exists by name
     */
    boolean existsByName(String name);

    /**
     * Find workflow by ID with all associations eagerly loaded for execution
     * Loads: workflow -> steps -> agent -> agentTools
     * This prevents lazy loading exceptions during async workflow execution
     */
    @Query("SELECT DISTINCT w FROM Workflow w " +
           "LEFT JOIN FETCH w.workflowSteps ws " +
           "LEFT JOIN FETCH ws.agent a " +
           "LEFT JOIN FETCH a.agentTools " +
           "WHERE w.id = :id")
    Optional<Workflow> findByIdWithStepsAndAgents(@Param("id") Long id);
}
