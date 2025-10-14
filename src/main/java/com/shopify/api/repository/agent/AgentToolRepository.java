package com.shopify.api.repository.agent;

import com.shopify.api.model.agent.AgentTool;
import org.springframework.data.jpa.repository.JpaRepository;
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
