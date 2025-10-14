package com.shopify.api.repository.agent;

import com.shopify.api.model.agent.Tool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Tool entity
 *
 * Provides database access methods for Tool operations.
 * Tools are dynamically registered and assigned to agents.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 */
@Repository
public interface ToolRepository extends JpaRepository<Tool, Long> {

    /**
     * Find a tool by name (unique)
     */
    Optional<Tool> findByName(String name);

    /**
     * Find all active tools
     */
    List<Tool> findByIsActiveTrue();

    /**
     * Find tools by type
     */
    List<Tool> findByType(String type);

    /**
     * Find active tools by type
     */
    List<Tool> findByTypeAndIsActiveTrue(String type);

    /**
     * Check if a tool exists by name
     */
    boolean existsByName(String name);
}
