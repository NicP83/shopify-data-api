package com.shopify.api.repository.agent;

import com.shopify.api.model.agent.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Agent entity
 *
 * Provides database access methods for Agent operations.
 * Supports zero-hardcoding principle - all agents are data, not code.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 */
@Repository
public interface AgentRepository extends JpaRepository<Agent, Long> {

    /**
     * Find an agent by name (unique)
     */
    Optional<Agent> findByName(String name);

    /**
     * Find all active agents
     */
    List<Agent> findByIsActiveTrue();

    /**
     * Find agents by model provider
     */
    List<Agent> findByModelProvider(String modelProvider);

    /**
     * Find agents by model provider and active status
     */
    List<Agent> findByModelProviderAndIsActiveTrue(String modelProvider);

    /**
     * Check if an agent exists by name
     */
    boolean existsByName(String name);

    /**
     * Find agents with specific tools
     */
    @Query("SELECT DISTINCT a FROM Agent a JOIN a.agentTools at WHERE at.tool.id = :toolId")
    List<Agent> findAgentsWithTool(Long toolId);

    /**
     * Find agent by ID with eagerly fetched agentTools and their tools
     * This prevents lazy loading exceptions when accessing agentTools outside of a transaction
     */
    @Query("SELECT a FROM Agent a LEFT JOIN FETCH a.agentTools at LEFT JOIN FETCH at.tool WHERE a.id = :id")
    Optional<Agent> findByIdWithTools(Long id);
}
