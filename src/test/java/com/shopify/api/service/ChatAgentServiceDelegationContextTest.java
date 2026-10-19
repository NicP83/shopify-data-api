package com.shopify.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopify.api.handler.tool.ChatToolHandler;
import com.shopify.api.handler.tool.DelegateToAgentChatToolHandler;
import com.shopify.api.model.ChatMessage;
import com.shopify.api.model.ChatRequest;
import com.shopify.api.model.ChatbotConfig;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The delegation gate reads the turn's config from the Reactor context; this
 * proves ChatAgentService actually puts it there for tools run in both paths.
 */
class ChatAgentServiceDelegationContextTest {

    /** Stands in for delegate_to_agent and records the config it can see. */
    static class ProbeTool implements ChatToolHandler {
        final AtomicReference<ChatbotConfig> seen = new AtomicReference<>();
        public String getToolName() { return "probe"; }
        public String getToolDescription() { return "probe"; }
        public JsonNode getInputSchema() {
            return new ObjectMapper().createObjectNode().put("type", "object");
        }
        public Mono<String> execute(JsonNode input) {
            return Mono.deferContextual(ctx -> {
                seen.set(ctx.<ChatbotConfig>getOrEmpty(
                        DelegateToAgentChatToolHandler.CHAT_CONFIG_CONTEXT_KEY).orElse(null));
                return Mono.just("{\"ok\":true}");
            });
        }
    }

    private static ChatRequest request() {
        ChatRequest r = new ChatRequest();
        r.setMessage("do you have paint?");
        return r;
    }

    @Test
    void blockingPathPutsTurnConfigInContext() {
        StubClaude stub = new StubClaude();
        ProbeTool probe = new ProbeTool();
        ChatAgentService service = stub.service(List.of(probe));
        ChatbotConfig persona = StubClaude.config();
        stub.responses.add(StubClaude.toolUseJson("probe", "{}"));
        stub.responses.add(StubClaude.textJson("Yes we do"));

        ChatMessage reply = service.processChat(request(), persona).block();

        assertThat(reply.getContent()).isEqualTo("Yes we do");
        assertThat(probe.seen.get()).isSameAs(persona);
    }

    @Test
    void streamingPathPutsTurnConfigInContext() {
        StubClaude stub = new StubClaude();
        ProbeTool probe = new ProbeTool();
        ChatAgentService service = stub.service(List.of(probe));
        ChatbotConfig persona = StubClaude.config();
        stub.responses.add(StubClaude.toolUseStream("probe", "{}", 100, 20));
        stub.responses.add(StubClaude.textStream(200, 30, "Yes ", "we do"));

        List<String> events = service.streamChat(request(), persona)
                .map(e -> e.event()).collectList().block();

        assertThat(events).containsExactly("status", "token", "token", "done");
        assertThat(probe.seen.get()).isSameAs(persona);
    }
}
