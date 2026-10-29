package com.shopify.api.controller;

import com.shopify.api.model.ChatAnalytics;
import com.shopify.api.model.ChatMessage;
import com.shopify.api.model.ChatRequest;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.service.ChatAgentService;
import com.shopify.api.service.ChatAnalyticsService;
import com.shopify.api.service.ChatTurnMetrics;
import com.shopify.api.service.ShopifyShopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Streamed chats are recorded like blocking ones, and recording can never
 * change, delay or break the frames the shopper receives.
 */
class ShopifyChatControllerStreamTrackingTest {

    private static final String SHOP = "hearnshobbies.myshopify.com";

    private ChatAgentService chatAgentService;
    private ChatAnalyticsService analyticsService;
    private ShopifyChatController controller;

    @BeforeEach
    void setUp() {
        chatAgentService = mock(ChatAgentService.class);
        analyticsService = mock(ChatAnalyticsService.class);
        ShopifyShopService shopService = mock(ShopifyShopService.class);
        ShopifyShop shop = new ShopifyShop();
        shop.setShopDomain(SHOP);
        shop.setAiEnabled(true);
        shop.setAiModel("claude-sonnet-4-6");
        shop.setAiTemperature(new java.math.BigDecimal("0.70"));
        shop.setAiMaxTokens(4096);
        when(shopService.isShopActive(SHOP)).thenReturn(true);
        when(shopService.getShop(SHOP)).thenReturn(shop);
        controller = new ShopifyChatController(chatAgentService, shopService, analyticsService);
    }

    private static ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }

    private static ChatRequest request() {
        ChatRequest r = new ChatRequest();
        r.setMessage("RG RX-78-2");
        r.setSessionId("s-1");
        return r;
    }

    /** A fake stream that also reports usage into the turn metrics, as ChatAgentService does. */
    private static Flux<ServerSentEvent<String>> fakeStream(List<ServerSentEvent<String>> frames) {
        return Flux.deferContextual(ctx -> {
            ChatTurnMetrics m = ChatTurnMetrics.from(ctx);
            m.recordClaudeCall("claude-sonnet-5-5", 10, 100, 20, 0, 0);
            m.recordClaudeCall("claude-sonnet-5-5", 10, 200, 30, 0, 0);
            m.recordTool("search_products", 40, true);
            return Flux.fromIterable(frames);
        });
    }

    private Flux<ServerSentEvent<String>> stream() {
        return controller.streamMessage(SHOP, request(), "Mozilla", "1.2.3.4, 5.6.7.8", null,
                "https://hearnshobbies.com/products/x");
    }

    private ChatAnalytics savedRow() {
        ArgumentCaptor<ChatAnalytics> captor = ArgumentCaptor.forClass(ChatAnalytics.class);
        verify(analyticsService, timeout(3000)).trackInteraction(captor.capture());
        return captor.getValue();
    }

    @Test
    void framesPassThroughUnchangedAndTurnIsRecorded() {
        List<ServerSentEvent<String>> frames = List.of(
                sse("status", "{\"text\":\"Searching…\"}"),
                sse("token", "{\"text\":\"We have \"}"),
                sse("token", "{\"text\":\"it\"}"),
                sse("done", "{}"));
        when(chatAgentService.streamChat(any())).thenReturn(fakeStream(frames));

        List<ServerSentEvent<String>> received = stream().collectList().block();

        assertThat(received).containsExactlyElementsOf(frames);
        ChatAnalytics row = savedRow();
        assertThat(row.getWasSuccessful()).isTrue();
        assertThat(row.getAssistantResponse()).isEqualTo("We have it");
        assertThat(row.getUserMessage()).isEqualTo("RG RX-78-2");
        assertThat(row.getSessionId()).isEqualTo("s-1");
        assertThat(row.getTokensUsed()).isEqualTo(350);
        assertThat(row.getApiCostUsd()).isPositive();
        assertThat(row.getModelUsed()).isEqualTo("claude-sonnet-5-5");
        assertThat(row.getToolResultsCount()).isEqualTo(1);
        assertThat(row.getIpAddress()).isEqualTo("5.6.7.8");   // right-most hop, never the client-set left
        assertThat(row.getUserAgent()).isEqualTo("Mozilla");
        assertThat(row.getErrorMessage()).isNull();
    }

    @Test
    void analyticsFailureDoesNotTouchTheStream() {
        List<ServerSentEvent<String>> frames = List.of(sse("token", "{\"text\":\"Hi\"}"), sse("done", "{}"));
        when(chatAgentService.streamChat(any())).thenReturn(fakeStream(frames));
        when(analyticsService.trackInteraction(any())).thenThrow(new RuntimeException("DB down"));

        List<ServerSentEvent<String>> received = stream().collectList().block();

        assertThat(received).containsExactlyElementsOf(frames);
        verify(analyticsService, timeout(3000)).trackInteraction(any());
    }

    @Test
    void slowAnalyticsDoesNotDelayTheStream() {
        List<ServerSentEvent<String>> frames = List.of(sse("token", "{\"text\":\"Hi\"}"), sse("done", "{}"));
        when(chatAgentService.streamChat(any())).thenReturn(fakeStream(frames));
        doAnswer(inv -> {
            Thread.sleep(2000);
            return inv.getArgument(0);
        }).when(analyticsService).trackInteraction(any());

        long start = System.currentTimeMillis();
        List<ServerSentEvent<String>> received = stream().collectList().block();
        long elapsed = System.currentTimeMillis() - start;

        assertThat(received).containsExactlyElementsOf(frames);
        assertThat(elapsed).isLessThan(1000);
    }

    @Test
    void errorFrameIsRecordedAsFailure() {
        List<ServerSentEvent<String>> frames = List.of(
                sse("token", "{\"text\":\"Hi\"}"), sse("error", "{\"message\":\"Claude 529 overloaded\"}"));
        when(chatAgentService.streamChat(any())).thenReturn(fakeStream(frames));

        assertThat(stream().collectList().block()).containsExactlyElementsOf(frames);

        ChatAnalytics row = savedRow();
        assertThat(row.getWasSuccessful()).isFalse();
        assertThat(row.getErrorMessage()).contains("Claude 529 overloaded");
    }

    @Test
    void exceptionInStreamIsRecordedAsFailureAndStillEndsWithErrorFrame() {
        when(chatAgentService.streamChat(any())).thenReturn(Flux.error(new IllegalStateException("boom")));

        List<ServerSentEvent<String>> received = stream().collectList().block();

        assertThat(received).extracting(ServerSentEvent::event).containsExactly("error");
        assertThat(savedRow().getWasSuccessful()).isFalse();
    }

    @Test
    void clientDisconnectRecordsWhatWasStreamed() {
        when(chatAgentService.streamChat(any())).thenReturn(
                Flux.concat(Flux.just(sse("token", "{\"text\":\"Partial\"}")), Flux.never()));

        // take(1) cancels upstream, as a closed browser tab does.
        List<ServerSentEvent<String>> received = stream().take(1).collectList().block();

        assertThat(received).hasSize(1);
        ChatAnalytics row = savedRow();
        assertThat(row.getWasSuccessful()).isTrue();
        assertThat(row.getAssistantResponse()).isEqualTo("Partial");
        assertThat(row.getErrorMessage()).isEqualTo("client disconnected");
    }

    @Test
    void blockingPathRecordsTokensSummedOverTheTurnAndActualModel() {
        ChatMessage reply = new ChatMessage("assistant", "We have it");
        reply.setInputTokens(200);
        reply.setOutputTokens(30);
        when(chatAgentService.processChat(any())).thenReturn(Mono.deferContextual(ctx -> {
            ChatTurnMetrics m = ChatTurnMetrics.from(ctx);
            m.recordClaudeCall("claude-sonnet-5-5", 10, 100, 20, 0, 0);
            m.recordClaudeCall("claude-sonnet-5-5", 10, 200, 30, 0, 0);
            return Mono.just(reply);
        }));

        var response = controller.sendMessage(SHOP, request(), "Mozilla", null, null, null).block();

        assertThat(response.getBody().get("response")).isEqualTo("We have it");
        ChatAnalytics row = savedRow();
        assertThat(row.getTokensUsed()).isEqualTo(350);
        assertThat(row.getModelUsed()).isEqualTo("claude-sonnet-5-5");
    }
}
