package com.shopify.api.repository.agent;

import com.shopify.api.model.agent.AgentExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for AgentExecution entity
 *
 * Tracks individual agent runs with performance metrics.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 */
@Repository
public interface AgentExecutionRepository extends JpaRepository<AgentExecution, Long> {

    /**
     * Find executions by workflow execution
     */
    List<AgentExecution> findByWorkflowExecutionId(Long workflowExecutionId);

    /**
     * Find executions by agent
     */
    List<AgentExecution> findByAgentId(Long agentId);

    /**
     * Find executions by status
     */
    List<AgentExecution> findByStatus(String status);

    /**
     * Find recent executions for an agent
     */
    List<AgentExecution> findTop10ByAgentIdOrderByCreatedAtDesc(Long agentId);

    /**
     * Find executions created within a time range
     */
    List<AgentExecution> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Calculate average execution time for an agent
     */
    @Query("SELECT AVG(ae.executionTimeMs) FROM AgentExecution ae WHERE ae.agent.id = :agentId AND ae.status = 'COMPLETED'")
    Double calculateAverageExecutionTime(Long agentId);

    /**
     * Calculate total tokens used by an agent
     */
    @Query("SELECT SUM(ae.tokensUsed) FROM AgentExecution ae WHERE ae.agent.id = :agentId")
    Long calculateTotalTokensUsed(Long agentId);

    /**
     * Count executions by agent and status
     */
    long countByAgentIdAndStatus(Long agentId, String status);
}
