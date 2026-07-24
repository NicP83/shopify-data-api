package com.shopify.api.controller.v1;

import com.shopify.api.filter.ApiKeyAuthFilter;
import com.shopify.api.model.ApiKey;
import com.shopify.api.model.ChatMessage;
import com.shopify.api.model.ChatRequest;
import com.shopify.api.model.ChatbotConfig;
import com.shopify.api.service.ChatAgentService;
import com.shopify.api.service.ChatbotConfigService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Chat with a named persona (assistant profile). Authenticated by an API key
 * with the "chat" scope. The persona's config (prompt, linked agents/workflows,
 * model) is resolved by slug and passed into ChatAgentService as a per-request
 * override — the storefront path is unaffected.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class PersonaChatController {

    private final ChatbotConfigService chatbotConfigService;
    private final ChatAgentService chatAgentService;

    @PostMapping("/chat")
    public Mono<ResponseEntity<Object>> chat(HttpServletRequest request,
                                             @RequestBody ChatBody body) {
        Object attr = request.getAttribute(ApiKeyAuthFilter.ATTR_API_KEY);
        if (!(attr instanceof ApiKey key) || !key.hasScope("chat")) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "chat scope required")));
        }
        if (body == null || body.getMessage() == null || body.getMessage().isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                .body(Map.of("error", "message is required")));
        }

        // Resolve the persona config; falls back to global if slug missing/unknown.
        ChatbotConfig personaConfig = chatbotConfigService.getConfigBySlug(body.getPersona());

        ChatRequest chatRequest = new ChatRequest(
            body.getMessage(), body.getConversationHistory(), body.getSessionId(), null);

        return chatAgentService.processChat(chatRequest, personaConfig)
            .map(msg -> ResponseEntity.ok((Object) msg))
            .onErrorResume(e -> {
                log.error("Persona chat failed: {}", e.getMessage(), e);
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body((Object) Map.of("error", e.getMessage())));
            });
    }

    @lombok.Data
    public static class ChatBody {
        private String message;
        private String persona;                       // profile slug
        private List<ChatMessage> conversationHistory;
        private String sessionId;
    }
}
