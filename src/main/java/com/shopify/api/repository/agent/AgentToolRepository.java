package com.shopify.api.repository.agent;

import com.shopify.api.model.agent.AgentTool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for AgentTool entity
 *
 * Manages the many-to-many relationship between agents and tools.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 */
@Repository
public interface AgentToolRepository extends JpaRepository<AgentTool, Long> {

    /**
     * Find all tools assigned to an agent
     */
    List<AgentTool> findByAgentId(Long agentId);

    /**
     * Find all assignments for an agent, eagerly fetching each Tool. Used by the
     * execution path so tool definitions are available without relying on the
     * Agent.agentTools lazy collection (which can come back empty across the
     * reactive boundary).
     */
    @Query("SELECT at FROM AgentTool at JOIN FETCH at.tool WHERE at.agent.id = :agentId")
    List<AgentTool> findByAgentIdWithTool(@Param("agentId") Long agentId);

    /**
     * Find all agents assigned to a tool
     */
    List<AgentTool> findByToolId(Long toolId);

    /**
     * Find a specific agent-tool assignment
     */
    Optional<AgentTool> findByAgentIdAndToolId(Long agentId, Long toolId);

    /**
     * Check if an agent has a specific tool
     */
    boolean existsByAgentIdAndToolId(Long agentId, Long toolId);

    /**
     * Delete all tools for an agent
     */
    void deleteByAgentId(Long agentId);
}
