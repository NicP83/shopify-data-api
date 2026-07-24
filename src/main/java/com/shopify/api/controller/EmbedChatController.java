package com.shopify.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.filter.ApiKeyAuthFilter;
import com.shopify.api.model.ApiKey;
import com.shopify.api.service.agent.AgentExecutionService;
import com.shopify.api.service.agent.WorkflowOrchestratorService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Conversational endpoint for the embeddable widget. Authenticated by an
 * "embed" API key that is bound to a single agent or workflow and (optionally)
 * a Referer allowlist. Stateless server-side: the client passes conversation
 * history, mirroring the storefront chatbot.
 *
 * Behind ApiKeyAuthFilter (see ApiKeyFilterConfig, path /api/embed/*).
 */
@RestController
@RequestMapping("/api/embed")
@RequiredArgsConstructor
@Slf4j
public class EmbedChatController {

    private final AgentExecutionService agentExecutionService;
    private final WorkflowOrchestratorService workflowOrchestratorService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/chat")
    public Mono<ResponseEntity<Object>> chat(HttpServletRequest request,
                                             @RequestBody Map<String, Object> body) {
        Object attr = request.getAttribute(ApiKeyAuthFilter.ATTR_API_KEY);
        if (!(attr instanceof ApiKey key) || !key.hasScope("embed")) {
            return Mono.just(err(HttpStatus.FORBIDDEN, "embed scope required"));
        }

        // Referer allowlist: if set, the request's Referer host must start with
        // one of the allowed entries.
        if (!refererAllowed(key, request.getHeader("Referer"))) {
            return Mono.just(err(HttpStatus.FORBIDDEN, "origin not allowed for this key"));
        }

        String message = body.get("message") == null ? null : body.get("message").toString();
        if (message == null || message.isBlank()) {
            return Mono.just(err(HttpStatus.BAD_REQUEST, "message is required"));
        }

        if (key.getBoundAgentId() != null) {
            ObjectNode input = objectMapper.createObjectNode();
            input.put("task", message);
            return agentExecutionService.executeAgent(key.getBoundAgentId(), input)
                .map(result -> ok(textOf(result.getOutput())))
                .onErrorResume(e -> Mono.just(err(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage())));
        }

        if (key.getBoundWorkflowId() != null) {
            ObjectNode trigger = objectMapper.createObjectNode();
            trigger.put("task", message);
            trigger.put("message", message);
            trigger.put("source", "embed");
            return workflowOrchestratorService.executeWorkflow(key.getBoundWorkflowId(), trigger)
                .map(result -> ok(result.getContext() != null ? result.getContext().toString()
                    : "Done."))
                .onErrorResume(e -> Mono.just(err(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage())));
        }

        return Mono.just(err(HttpStatus.BAD_REQUEST,
            "This embed key is not bound to an agent or workflow"));
    }

    /**
     * Host-based Referer check. Allowlist entries are hostnames (e.g.
     * "hearnshobbies.com"); a request is allowed when the Referer host equals
     * an entry or is a subdomain of it. Uses host equality/suffix rather than
     * string prefix so "hearnshobbies.com.evil.com" cannot pass.
     */
    private boolean refererAllowed(ApiKey key, String referer) {
        var allow = key.getRefererAllowlistEntries();
        if (allow.isEmpty()) return true;      // unrestricted
        if (referer == null) return false;     // restricted but no referer sent
        String host;
        try {
            host = java.net.URI.create(referer).getHost();
        } catch (IllegalArgumentException e) {
            return false;                      // unparseable Referer -> reject
        }
        if (host == null) return false;
        final String h = host.toLowerCase().replaceAll("\\.$", "");
        return allow.stream()
            .map(e -> e.toLowerCase().replaceAll("\\.$", ""))
            .anyMatch(e -> h.equals(e) || h.endsWith("." + e));
    }

    private String textOf(JsonNode output) {
        if (output == null) return "";
        return output.has("text") ? output.get("text").asText() : output.toString();
    }

    private ResponseEntity<Object> ok(String responseText) {
        ObjectNode resp = objectMapper.createObjectNode();
        resp.put("role", "assistant");
        resp.put("response", responseText);
        return ResponseEntity.ok(resp);
    }

    private ResponseEntity<Object> err(HttpStatus status, String message) {
        return ResponseEntity.status(status)
            .body(Map.of("error", message == null ? "error" : message));
    }
}
