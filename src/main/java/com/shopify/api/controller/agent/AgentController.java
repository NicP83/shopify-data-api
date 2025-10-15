package com.shopify.api.controller.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopify.api.dto.agent.AgentResponse;
import com.shopify.api.dto.agent.CreateAgentRequest;
import com.shopify.api.model.agent.Agent;
import com.shopify.api.model.agent.AgentExecution;
import com.shopify.api.repository.agent.AgentExecutionRepository;
import com.shopify.api.service.agent.AgentExecutionService;
import com.shopify.api.service.agent.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for Agent management
 *
 * Provides endpoints for creating, reading, updating, and deleting agents.
 * Implements zero-hardcoding principle - all agents are data-driven.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AgentController {

    private final AgentService agentService;
    private final AgentExecutionService agentExecutionService;
    private final AgentExecutionRepository agentExecutionRepository;

    /**
     * Create a new agent
     * POST /api/agents
     */
    @PostMapping
    public ResponseEntity<AgentResponse> createAgent(@Valid @RequestBody CreateAgentRequest request) {
        log.info("Creating new agent: {}", request.getName());

        Agent agent = Agent.builder()
            .name(request.getName())
            .description(request.getDescription())
            .modelProvider(request.getModelProvider())
            .modelName(request.getModelName())
            .systemPrompt(request.getSystemPrompt())
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxTokens())
            .configJson(request.getConfigJson())
            .isActive(request.getIsActive())
            .build();

        Agent createdAgent = agentService.createAgent(agent);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(AgentResponse.fromEntity(createdAgent));
    }

    /**
     * Get all agents
     * GET /api/agents
     */
    @GetMapping
    public ResponseEntity<List<AgentResponse>> getAllAgents(
        @RequestParam(required = false) Boolean activeOnly,
        @RequestParam(required = false) String provider
    ) {
        log.info("Fetching agents - activeOnly: {}, provider: {}", activeOnly, provider);

        List<Agent> agents;

        if (provider != null) {
            agents = agentService.getAgentsByProvider(provider);
        } else if (Boolean.TRUE.equals(activeOnly)) {
            agents = agentService.getActiveAgents();
        } else {
            agents = agentService.getAllAgents();
        }

        List<AgentResponse> response = agents.stream()
            .map(AgentResponse::fromEntity)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get agent by ID
     * GET /api/agents/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<AgentResponse> getAgentById(@PathVariable Long id) {
        log.info("Fetching agent with ID: {}", id);

        return agentService.getAgentById(id)
            .map(AgentResponse::fromEntity)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get agent by name
     * GET /api/agents/by-name/{name}
     */
    @GetMapping("/by-name/{name}")
    public ResponseEntity<AgentResponse> getAgentByName(@PathVariable String name) {
        log.info("Fetching agent with name: {}", name);

        return agentService.getAgentByName(name)
            .map(AgentResponse::fromEntity)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update an existing agent
     * PUT /api/agents/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<AgentResponse> updateAgent(
        @PathVariable Long id,
        @Valid @RequestBody CreateAgentRequest request
    ) {
        log.info("Updating agent with ID: {}", id);

        Agent updatedAgent = Agent.builder()
            .name(request.getName())
            .description(request.getDescription())
            .modelProvider(request.getModelProvider())
            .modelName(request.getModelName())
            .systemPrompt(request.getSystemPrompt())
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxTokens())
            .configJson(request.getConfigJson())
            .isActive(request.getIsActive())
            .build();

        Agent agent = agentService.updateAgent(id, updatedAgent);
        return ResponseEntity.ok(AgentResponse.fromEntity(agent));
    }

    /**
     * Delete an agent
     * DELETE /api/agents/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAgent(@PathVariable Long id) {
        log.info("Deleting agent with ID: {}", id);
        agentService.deleteAgent(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Activate an agent
     * POST /api/agents/{id}/activate
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activateAgent(@PathVariable Long id) {
        log.info("Activating agent with ID: {}", id);
        agentService.activateAgent(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Deactivate an agent
     * POST /api/agents/{id}/deactivate
     */
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateAgent(@PathVariable Long id) {
        log.info("Deactivating agent with ID: {}", id);
        agentService.deactivateAgent(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Assign a tool to an agent
     * POST /api/agents/{agentId}/tools/{toolId}
     */
    @PostMapping("/{agentId}/tools/{toolId}")
    public ResponseEntity<Void> assignTool(
        @PathVariable Long agentId,
        @PathVariable Long toolId
    ) {
        log.info("Assigning tool {} to agent {}", toolId, agentId);
        agentService.assignToolToAgent(agentId, toolId);
        return ResponseEntity.ok().build();
    }

    /**
     * Remove a tool from an agent
     * DELETE /api/agents/{agentId}/tools/{toolId}
     */
    @DeleteMapping("/{agentId}/tools/{toolId}")
    public ResponseEntity<Void> removeTool(
        @PathVariable Long agentId,
        @PathVariable Long toolId
    ) {
        log.info("Removing tool {} from agent {}", toolId, agentId);
        agentService.removeToolFromAgent(agentId, toolId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get all tools assigned to an agent
     * GET /api/agents/{agentId}/tools
     */
    @GetMapping("/{agentId}/tools")
    public ResponseEntity<?> getAgentTools(@PathVariable Long agentId) {
        log.info("Fetching tools for agent {}", agentId);
        return ResponseEntity.ok(agentService.getAgentTools(agentId));
    }

    /**
     * Execute an agent with given input
     * POST /api/agents/{id}/execute
     */
    @PostMapping("/{id}/execute")
    public Mono<ResponseEntity<Object>> executeAgent(
        @PathVariable Long id,
        @RequestBody JsonNode input
    ) {
        log.info("Executing agent {} with input", id);

        return agentExecutionService.executeAgent(id, input)
            .map(result -> ResponseEntity.ok((Object) result))
            .onErrorResume(error -> {
                log.error("Agent execution failed: {}", error.getMessage(), error);
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body((Object) new ErrorResponse(error.getMessage())));
            });
    }

    /**
     * Get execution history for an agent
     * GET /api/agents/{agentId}/executions
     */
    @GetMapping("/{agentId}/executions")
    public ResponseEntity<List<AgentExecution>> getAgentExecutions(@PathVariable Long agentId) {
        log.info("Fetching executions for agent {}", agentId);
        List<AgentExecution> executions = agentExecutionRepository.findByAgentId(agentId);
        return ResponseEntity.ok(executions);
    }

    /**
     * Exception handler for IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        log.error("Validation error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    /**
     * Simple error response class
     */
    private record ErrorResponse(String error) {}
}
