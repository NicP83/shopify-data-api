package com.shopify.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopify.api.handler.tool.ChatToolHandler;
import com.shopify.api.model.ChatMessage;
import com.shopify.api.model.ChatRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChatAgentService fills the turn metrics (Claude usage summed over the tool
 * loop, each tool's duration) in both paths, and works unchanged without them.
 */
class ChatAgentServiceTurnMetricsTest {

    /** A tool that takes ~50 ms. */
    static class SlowTool implements ChatToolHandler {
        public String getToolName() { return "search_products"; }
        public String getToolDescription() { return "search"; }
        public JsonNode getInputSchema() {
            return new ObjectMapper().createObjectNode().put("type", "object");
        }
        public Mono<String> execute(JsonNode input) {
            return Mono.just("{\"products\":[]}").delayElement(Duration.ofMillis(50));
        }
    }

    private static ChatRequest request() {
        ChatRequest r = new ChatRequest();
        r.setMessage("RG RX-78-2");
        return r;
    }

    @Test
    void streamedTurnSumsUsageAndTimesTools() {
        StubClaude stub = new StubClaude();
        ChatAgentService service = stub.service(List.of(new SlowTool()));
        stub.responses.add(StubClaude.toolUseStream("search_products", "{\"query\":\"RX-78-2\"}", 100, 20));
        stub.responses.add(StubClaude.textStream(200, 30, "We have ", "it"));
        ChatTurnMetrics metrics = new ChatTurnMetrics("stream");

        List<String> events = service.streamChat(request(), StubClaude.config())
                .map(ServerSentEvent::event)
                .contextWrite(ctx -> ctx.put(ChatTurnMetrics.CONTEXT_KEY, metrics))
                .collectList().block();

        assertThat(events).containsExactly("status", "token", "token", "done");
        assertThat(metrics.hasUsage()).isTrue();
        assertThat(metrics.getInputTokens()).isEqualTo(300);
        assertThat(metrics.getOutputTokens()).isEqualTo(50);
        assertThat(metrics.getModel()).isEqualTo("claude-sonnet-5-5");
        assertThat(metrics.getToolCount()).isEqualTo(1);
        String line = metrics.toLogLine("ok", 123);
        assertThat(line).contains("path=stream", "claudeCalls=2", "cacheRead=7", "tools=[search_products:");
        assertThat(line).doesNotContain("(failed)");
    }

    @Test
    void blockingTurnSumsUsageAndTimesTools() {
        StubClaude stub = new StubClaude();
        ChatAgentService service = stub.service(List.of(new SlowTool()));
        stub.responses.add(StubClaude.toolUseJson("search_products", "{\"query\":\"RX-78-2\"}"));
        stub.responses.add(StubClaude.textJson("We have it"));
        ChatTurnMetrics metrics = new ChatTurnMetrics("blocking");

        ChatMessage reply = service.processChat(request(), StubClaude.config())
                .contextWrite(ctx -> ctx.put(ChatTurnMetrics.CONTEXT_KEY, metrics))
                .block();

        assertThat(reply.getContent()).isEqualTo("We have it");
        // Final call's tokens are still on the message, as before.
        assertThat(reply.getInputTokens()).isEqualTo(200);
        assertThat(metrics.getInputTokens()).isEqualTo(300);
        assertThat(metrics.getOutputTokens()).isEqualTo(50);
        assertThat(metrics.getToolCount()).isEqualTo(1);
        assertThat(metrics.toLogLine("ok", 1)).contains("claudeCalls=2");
    }

    @Test
    void worksWithoutMetricsInContext() {
        StubClaude stub = new StubClaude();
        ChatAgentService service = stub.service(List.of(new SlowTool()));
        stub.responses.add(StubClaude.toolUseStream("search_products", "{}", 1, 1));
        stub.responses.add(StubClaude.textStream(1, 1, "ok"));

        List<String> events = service.streamChat(request(), StubClaude.config())
                .map(ServerSentEvent::event).collectList().block();

        assertThat(events).containsExactly("status", "token", "done");
    }
}
