package com.shopify.api.service.agent;

import com.shopify.api.config.ModelConfig;
import com.shopify.api.model.agent.Agent;
import com.shopify.api.model.agent.AgentTool;
import com.shopify.api.repository.agent.AgentRepository;
import com.shopify.api.repository.agent.AgentToolRepository;
import com.shopify.api.repository.agent.ToolRepository;
import com.shopify.api.service.ModelValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private final ModelValidationService modelValidationService;

    /**
     * Create a new agent
     */
    @Transactional
    public Agent createAgent(Agent agent) {
        log.info("Creating new agent: {}", agent.getName());

        if (agentRepository.existsByName(agent.getName())) {
            throw new IllegalArgumentException("Agent with name '" + agent.getName() + "' already exists");
        }

        // Validate and normalize model before saving
        String validatedModel = modelValidationService.validateOrDefault(agent.getModelName());
        if (!validatedModel.equals(agent.getModelName())) {
            log.warn("Invalid model '{}' for new agent '{}', using '{}' instead",
                agent.getModelName(), agent.getName(), validatedModel);
            agent.setModelName(validatedModel);
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
     * Get agent by ID with tools eagerly loaded
     */
    @Transactional(readOnly = true)
    public Optional<Agent> getAgentByIdWithTools(Long id) {
        return agentRepository.findByIdWithTools(id);
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

        // Validate and normalize model before updating
        String validatedModel = modelValidationService.validateOrDefault(updatedAgent.getModelName());
        if (!validatedModel.equals(updatedAgent.getModelName())) {
            log.warn("Invalid model '{}' for agent '{}', using '{}' instead",
                updatedAgent.getModelName(), updatedAgent.getName(), validatedModel);
        }

        // Update fields
        existingAgent.setName(updatedAgent.getName());
        existingAgent.setDescription(updatedAgent.getDescription());
        existingAgent.setModelProvider(updatedAgent.getModelProvider());
        existingAgent.setModelName(validatedModel);  // Use validated model
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
     * Reconcile an agent's tool assignments with the given list.
     * A null list means "leave assignments untouched" (backward compatible
     * with callers that manage tools via the attach/detach endpoints).
     */
    @Transactional
    public void syncAgentTools(Long agentId, List<Long> toolIds) {
        if (toolIds == null) {
            return;
        }
        log.info("Syncing tools for agent {}: {}", agentId, toolIds);

        Set<Long> desired = new HashSet<>(toolIds);
        List<AgentTool> current = agentToolRepository.findByAgentId(agentId);

        for (AgentTool assignment : current) {
            Long toolId = assignment.getTool().getId();
            if (!desired.contains(toolId)) {
                removeToolFromAgent(agentId, toolId);
            } else {
                desired.remove(toolId);
            }
        }
        for (Long toolId : desired) {
            assignToolToAgent(agentId, toolId);
        }
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
