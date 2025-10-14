package com.shopify.api.repository.agent;

import com.shopify.api.model.agent.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
