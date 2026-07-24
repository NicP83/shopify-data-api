package com.shopify.api.controller.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.filter.ApiKeyAuthFilter;
import com.shopify.api.model.ApiKey;
import com.shopify.api.model.agent.Agent;
import com.shopify.api.model.agent.Workflow;
import com.shopify.api.service.agent.AgentExecutionService;
import com.shopify.api.service.agent.AgentService;
import com.shopify.api.service.agent.WorkflowOrchestratorService;
import com.shopify.api.service.agent.WorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Versioned external API for other systems (e.g. the HR app) to list and run
 * agents and workflows. All routes are behind ApiKeyAuthFilter; each requires
 * the matching scope on the caller's key.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ExternalApiController {

    private final AgentService agentService;
    private final AgentExecutionService agentExecutionService;
    private final WorkflowService workflowService;
    private final WorkflowOrchestratorService workflowOrchestratorService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---- Agents ----

    @GetMapping("/agents")
    public ResponseEntity<?> listAgents(HttpServletRequest request) {
        ResponseEntity<?> denied = requireScope(request, "agents:run");
        if (denied != null) return denied;
        List<Map<String, Object>> agents = agentService.getActiveAgents().stream()
            .map(a -> Map.<String, Object>of(
                "id", a.getId(),
                "name", a.getName(),
                "description", a.getDescription() == null ? "" : a.getDescription()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(agents);
    }

    @PostMapping("/agents/{id}/run")
    public Mono<ResponseEntity<Object>> runAgent(HttpServletRequest request,
                                                 @PathVariable Long id,
                                                 @RequestBody(required = false) Map<String, Object> body) {
        ResponseEntity<?> denied = requireScope(request, "agents:run");
        if (denied != null) return Mono.just(cast(denied));

        String task = extractTask(body);
        if (task == null || task.isBlank()) {
            return Mono.just(cast(ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "'task' or 'input' is required"))));
        }

        Agent agent = agentService.getAgentById(id).orElse(null);
        if (agent == null) {
            return Mono.just(cast(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("success", false, "error", "Agent not found: " + id))));
        }

        ObjectNode input = objectMapper.createObjectNode();
        input.put("task", task);

        return agentExecutionService.executeAgent(id, input)
            .map(result -> {
                ObjectNode resp = objectMapper.createObjectNode();
                resp.put("success", result.isSuccess());
                resp.set("result", result.getOutput());
                resp.put("tokensUsed", result.getInputTokens() + result.getOutputTokens());
                if (result.getErrorMessage() != null) resp.put("error", result.getErrorMessage());
                return ResponseEntity.ok((Object) resp);
            })
            .onErrorResume(e -> {
                log.error("External agent run failed for {}: {}", id, e.getMessage(), e);
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body((Object) Map.of("success", false, "error", e.getMessage())));
            });
    }

    // ---- Workflows ----

    @GetMapping("/workflows")
    public ResponseEntity<?> listWorkflows(HttpServletRequest request) {
        ResponseEntity<?> denied = requireScope(request, "workflows:run");
        if (denied != null) return denied;
        List<Map<String, Object>> workflows = workflowService.getActiveWorkflows().stream()
            .map(w -> Map.<String, Object>of(
                "id", w.getId(),
                "name", w.getName(),
                "description", w.getDescription() == null ? "" : w.getDescription()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(workflows);
    }

    @PostMapping("/workflows/{id}/run")
    public Mono<ResponseEntity<Object>> runWorkflow(HttpServletRequest request,
                                                    @PathVariable Long id,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        ResponseEntity<?> denied = requireScope(request, "workflows:run");
        if (denied != null) return Mono.just(cast(denied));

        Workflow workflow = workflowService.getWorkflowById(id).orElse(null);
        if (workflow == null) {
            return Mono.just(cast(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("success", false, "error", "Workflow not found: " + id))));
        }

        JsonNode triggerData = body == null
            ? objectMapper.createObjectNode()
            : objectMapper.valueToTree(body);

        return workflowOrchestratorService.executeWorkflow(id, triggerData)
            .map(result -> {
                ObjectNode resp = objectMapper.createObjectNode();
                resp.put("success", result.isSuccess());
                if (result.getContext() != null) resp.set("result", result.getContext());
                if (result.getErrorMessage() != null) resp.put("error", result.getErrorMessage());
                return ResponseEntity.ok((Object) resp);
            })
            .onErrorResume(e -> {
                log.error("External workflow run failed for {}: {}", id, e.getMessage(), e);
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body((Object) Map.of("success", false, "error", e.getMessage())));
            });
    }

    // ---- helpers ----

    private String extractTask(Map<String, Object> body) {
        if (body == null) return null;
        Object task = body.get("task");
        if (task == null) task = body.get("input");
        if (task == null) task = body.get("message");
        return task == null ? null : task.toString();
    }

    private ResponseEntity<?> requireScope(HttpServletRequest request, String scope) {
        Object attr = request.getAttribute(ApiKeyAuthFilter.ATTR_API_KEY);
        if (!(attr instanceof ApiKey key) || !key.hasScope(scope)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("success", false, "error", "scope '" + scope + "' required"));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Object> cast(ResponseEntity<?> re) {
        return (ResponseEntity<Object>) re;
    }
}
