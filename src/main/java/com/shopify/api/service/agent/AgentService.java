package com.shopify.api.service.agent;

import com.shopify.api.model.agent.Agent;
import com.shopify.api.model.agent.AgentTool;
import com.shopify.api.repository.agent.AgentRepository;
import com.shopify.api.repository.agent.AgentToolRepository;
import com.shopify.api.repository.agent.ToolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing AI agents
 *
 * Implements zero-hardcoding principle - all agents are dynamically
 * created and configured via database records.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {

    private final AgentRepository agentRepository;
    private final AgentToolRepository agentToolRepository;
    private final ToolRepository toolRepository;

    /**
     * Create a new agent
     */
    @Transactional
    public Agent createAgent(Agent agent) {
        log.info("Creating new agent: {}", agent.getName());

        if (agentRepository.existsByName(agent.getName())) {
            throw new IllegalArgumentException("Agent with name '" + agent.getName() + "' already exists");
        }

        return agentRepository.save(agent);
    }

    /**
     * Get agent by ID
     */
    @Transactional(readOnly = true)
    public Optional<Agent> getAgentById(Long id) {
        return agentRepository.findById(id);
    }

    /**
     * Get agent by name
     */
    @Transactional(readOnly = true)
    public Optional<Agent> getAgentByName(String name) {
        return agentRepository.findByName(name);
    }

    /**
     * Get all agents
     */
    @Transactional(readOnly = true)
    public List<Agent> getAllAgents() {
        return agentRepository.findAll();
    }

    /**
     * Get all active agents
     */
    @Transactional(readOnly = true)
    public List<Agent> getActiveAgents() {
        return agentRepository.findByIsActiveTrue();
    }

    /**
     * Get agents by model provider
     */
    @Transactional(readOnly = true)
    public List<Agent> getAgentsByProvider(String provider) {
        return agentRepository.findByModelProvider(provider);
    }

    /**
     * Update an existing agent
     */
    @Transactional
    public Agent updateAgent(Long id, Agent updatedAgent) {
        log.info("Updating agent with ID: {}", id);

        Agent existingAgent = agentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Agent not found with ID: " + id));

        // Check if name is being changed and if new name already exists
        if (!existingAgent.getName().equals(updatedAgent.getName()) &&
            agentRepository.existsByName(updatedAgent.getName())) {
            throw new IllegalArgumentException("Agent with name '" + updatedAgent.getName() + "' already exists");
        }

        // Update fields
        existingAgent.setName(updatedAgent.getName());
        existingAgent.setDescription(updatedAgent.getDescription());
        existingAgent.setModelProvider(updatedAgent.getModelProvider());
        existingAgent.setModelName(updatedAgent.getModelName());
        existingAgent.setSystemPrompt(updatedAgent.getSystemPrompt());
        existingAgent.setTemperature(updatedAgent.getTemperature());
        existingAgent.setMaxTokens(updatedAgent.getMaxTokens());
        existingAgent.setConfigJson(updatedAgent.getConfigJson());
        existingAgent.setIsActive(updatedAgent.getIsActive());

        return agentRepository.save(existingAgent);
    }

    /**
     * Delete an agent
     */
    @Transactional
    public void deleteAgent(Long id) {
        log.info("Deleting agent with ID: {}", id);

        if (!agentRepository.existsById(id)) {
            throw new IllegalArgumentException("Agent not found with ID: " + id);
        }

        agentRepository.deleteById(id);
    }

    /**
     * Assign a tool to an agent
     */
    @Transactional
    public AgentTool assignToolToAgent(Long agentId, Long toolId) {
        log.info("Assigning tool {} to agent {}", toolId, agentId);

        Agent agent = agentRepository.findById(agentId)
            .orElseThrow(() -> new IllegalArgumentException("Agent not found with ID: " + agentId));

        toolRepository.findById(toolId)
            .orElseThrow(() -> new IllegalArgumentException("Tool not found with ID: " + toolId));

        if (agentToolRepository.existsByAgentIdAndToolId(agentId, toolId)) {
            throw new IllegalArgumentException("Tool is already assigned to this agent");
        }

        AgentTool agentTool = AgentTool.builder()
            .agent(agent)
            .tool(toolRepository.findById(toolId).get())
            .build();

        return agentToolRepository.save(agentTool);
    }

    /**
     * Remove a tool from an agent
     */
    @Transactional
    public void removeToolFromAgent(Long agentId, Long toolId) {
        log.info("Removing tool {} from agent {}", toolId, agentId);

        AgentTool agentTool = agentToolRepository.findByAgentIdAndToolId(agentId, toolId)
            .orElseThrow(() -> new IllegalArgumentException("Tool assignment not found"));

        agentToolRepository.delete(agentTool);
    }

    /**
     * Get all tools assigned to an agent
     */
    @Transactional(readOnly = true)
    public List<AgentTool> getAgentTools(Long agentId) {
        return agentToolRepository.findByAgentId(agentId);
    }

    /**
     * Activate an agent
     */
    @Transactional
    public void activateAgent(Long id) {
        log.info("Activating agent with ID: {}", id);

        Agent agent = agentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Agent not found with ID: " + id));

        agent.setIsActive(true);
        agentRepository.save(agent);
    }

    /**
     * Deactivate an agent
     */
    @Transactional
    public void deactivateAgent(Long id) {
        log.info("Deactivating agent with ID: {}", id);

        Agent agent = agentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Agent not found with ID: " + id));

        agent.setIsActive(false);
        agentRepository.save(agent);
    }
}
