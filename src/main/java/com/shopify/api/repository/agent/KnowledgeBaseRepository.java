package com.shopify.api.repository.agent;

import com.shopify.api.model.agent.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for KnowledgeBase entity
 *
 * Manages agent-specific knowledge base content for RAG.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 */
@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    /**
     * Find all knowledge base entries for an agent
     */
    List<KnowledgeBase> findByAgentId(Long agentId);

    /**
     * Find knowledge base entries by name
     */
    List<KnowledgeBase> findByName(String name);

    /**
     * Search knowledge base content by keyword
     */
    @Query("SELECT kb FROM KnowledgeBase kb WHERE LOWER(kb.content) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<KnowledgeBase> searchByContent(String keyword);

    /**
     * Search knowledge base for an agent by keyword
     */
    @Query("SELECT kb FROM KnowledgeBase kb WHERE kb.agent.id = :agentId AND LOWER(kb.content) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<KnowledgeBase> searchByAgentAndContent(Long agentId, String keyword);

    /**
     * Count knowledge base entries for an agent
     */
    long countByAgentId(Long agentId);

    /**
     * Delete all knowledge base entries for an agent
     */
    void deleteByAgentId(Long agentId);
}
