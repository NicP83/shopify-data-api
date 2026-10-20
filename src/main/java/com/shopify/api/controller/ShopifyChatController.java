package com.shopify.api.controller;

import com.shopify.api.model.ChatAnalytics;
import com.shopify.api.model.ChatRequest;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.service.ChatAgentService;
import com.shopify.api.service.ChatAnalyticsService;
import com.shopify.api.service.ChatTurnMetrics;
import com.shopify.api.service.voucher.VoucherChatContext;
import reactor.util.context.Context;
import com.shopify.api.service.ShopifyShopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * REST API for shop-scoped chat with AI assistant
 * Accessible from Shopify storefronts (CORS enabled)
 */
@RestController
@RequestMapping("/api/shopify/chat")
@CrossOrigin(
    originPatterns = {
        "https://hearnshobbies.com",
        "https://www.hearnshobbies.com",
        "https://*.myshopify.com"
    },
    allowedHeaders = "*",
    methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS},
    allowCredentials = "true"
)
public class ShopifyChatController {

    private static final Logger logger = LoggerFactory.getLogger(ShopifyChatController.class);

    private final ChatAgentService chatAgentService;
    private final ShopifyShopService shopifyShopService;
    private final ChatAnalyticsService chatAnalyticsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public ShopifyChatController(ChatAgentService chatAgentService,
                                  ShopifyShopService shopifyShopService,
                                  ChatAnalyticsService chatAnalyticsService) {
        this.chatAgentService = chatAgentService;
        this.shopifyShopService = shopifyShopService;
        this.chatAnalyticsService = chatAnalyticsService;
    }

    /**
     * Send message to AI assistant (shop-scoped)
     * POST /api/shopify/chat/message?shop=hearnshobbies.myshopify.com
     */
    @PostMapping("/message")
    public Mono<ResponseEntity<Map<String, Object>>> sendMessage(
            @RequestParam String shop,
            @RequestBody ChatRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Forwarded-For", required = false) String xForwardedFor,
            @RequestHeader(value = "X-Real-IP", required = false) String xRealIp,
            @RequestHeader(value = "Referer", required = false) String referer) {

        logger.info("Chat message received for shop: {} (session: {})", shop, request.getSessionId());

        // Start timer for response time tracking
        final long startTime = System.currentTimeMillis();

        try {
            // Validate shop is installed and active
            if (!shopifyShopService.isShopActive(shop)) {
                logger.warn("Shop not found or inactive: {}", shop);

                // Track failed interaction
                trackFailedInteraction(shop, request, "Shop not found or inactive", startTime);

                Map<String, Object> error = new HashMap<>();
                error.put("error", "Shop not found or inactive");
                error.put("shop", shop);
                return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(error));
            }

            // Get shop configuration
            ShopifyShop shopConfig = shopifyShopService.getShop(shop);

            // Check if AI is enabled for this shop
            if (!shopConfig.getAiEnabled()) {
                logger.warn("AI disabled for shop: {}", shop);

                // Track failed interaction
                trackFailedInteraction(shop, request, "AI disabled for this shop", startTime);

                Map<String, Object> error = new HashMap<>();
                error.put("error", "AI assistant is disabled for this shop");
                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(error));
            }

            // Apply shop-specific AI configuration to ChatAgentService
            chatAgentService.setAnthropicModel(shopConfig.getAiModel());
            chatAgentService.setTemperature(shopConfig.getAiTemperatureValue());
            chatAgentService.setMaxTokens(shopConfig.getAiMaxTokensValue());

            // Set shop context for dynamic system prompts
            chatAgentService.setShopContext(shopConfig);

            logger.debug("Applied shop-specific config: model={}, temp={}, maxTokens={}",
                shopConfig.getAiModel(),
                shopConfig.getAiTemperatureValue(),
                shopConfig.getAiMaxTokensValue());

            ChatTurnMetrics metrics = new ChatTurnMetrics("blocking");

            // Process chat request with shop-specific settings
            return chatAgentService.processChat(request)
                .map(chatMessage -> {
                    // Calculate response time
                    long responseTime = System.currentTimeMillis() - startTime;
                    logTurn(metrics, "ok", responseTime);

                    // Track successful interaction with token usage, summed over every
                    // Claude call of the turn when available (else the final call's).
                    boolean summed = metrics.hasUsage();
                    trackSuccessfulInteraction(shopConfig, request, chatMessage.getContent(),
                        responseTime,
                        summed ? Integer.valueOf(metrics.getInputTokens()) : chatMessage.getInputTokens(),
                        summed ? Integer.valueOf(metrics.getOutputTokens()) : chatMessage.getOutputTokens(),
                        metrics.getModel(), metrics.getToolCount(),
                        userAgent, xForwardedFor, xRealIp, referer);

                    Map<String, Object> response = new HashMap<>();
                    response.put("response", chatMessage.getContent());
                    response.put("role", chatMessage.getRole());
                    response.put("timestamp", java.time.Instant.now().toString());
                    return ResponseEntity.ok(response);
                })
                .doFinally(signalType -> {
                    // Clear shop context after request completes
                    chatAgentService.clearShopContext();
                    logger.debug("Cleared shop context for: {}", shop);
                })
                .onErrorResume(e -> {
                    logger.error("Error processing chat for shop {}: {}", shop, e.getMessage());

                    // Calculate response time for error case
                    long responseTime = System.currentTimeMillis() - startTime;
                    logTurn(metrics, "error", responseTime);

                    // Track failed interaction with error details
                    trackFailedInteractionWithShop(shopConfig, request,
                        "Chat processing failed: " + e.getMessage(), responseTime);

                    chatAgentService.clearShopContext();
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "Chat processing failed");
                    error.put("message", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
                })
                .contextWrite(ctx -> withVoucherContext(ctx, request, xForwardedFor, xRealIp))
                .contextWrite(ctx -> ctx.put(ChatTurnMetrics.CONTEXT_KEY, metrics));

        } catch (Exception e) {
            logger.error("Error in sendMessage: {}", e.getMessage(), e);

            // Calculate response time for exception case
            long responseTime = System.currentTimeMillis() - startTime;

            // Track failed interaction
            trackFailedInteraction(shop, request, "Internal server error: " + e.getMessage(),
                responseTime);

            Map<String, Object> error = new HashMap<>();
            error.put("error", "Internal server error");
            error.put("message", e.getMessage());
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
        }
    }

    /**
     * Streaming variant of {@link #sendMessage}: returns Server-Sent Events so the
     * storefront widget can render the answer as it is generated. The blocking
     * endpoint above is unchanged; the widget falls back to it on any stream error.
     * POST /api/shopify/chat/message/stream?shop=hearnshobbies.myshopify.com
     */
    @PostMapping(value = "/message/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamMessage(
            @RequestParam String shop,
            @RequestBody ChatRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Forwarded-For", required = false) String xForwardedFor,
            @RequestHeader(value = "X-Real-IP", required = false) String xRealIp,
            @RequestHeader(value = "Referer", required = false) String referer) {
        try {
            if (!shopifyShopService.isShopActive(shop)) {
                logger.warn("Stream: shop not found or inactive: {}", shop);
                return Flux.just(sseError("Shop not found or inactive"));
            }
            ShopifyShop shopConfig = shopifyShopService.getShop(shop);
            if (!shopConfig.getAiEnabled()) {
                logger.warn("Stream: AI disabled for shop: {}", shop);
                return Flux.just(sseError("AI assistant is disabled for this shop"));
            }

            // Apply shop-specific AI configuration (mirrors the blocking endpoint)
            chatAgentService.setAnthropicModel(shopConfig.getAiModel());
            chatAgentService.setTemperature(shopConfig.getAiTemperatureValue());
            chatAgentService.setMaxTokens(shopConfig.getAiMaxTokensValue());
            chatAgentService.setShopContext(shopConfig);

            // Merge a periodic SSE comment "heartbeat" into the stream so the connection is
            // never idle for more than ~15s during a long token-less phase (e.g. a 40s+ expert
            // delegation). Without it, an intermediary (Railway edge) can reap the idle socket,
            // which surfaces as an abrupt reset and trips the client's blocking fallback.
            // The content stream always ends with exactly one "done" or "error" frame, so an
            // inclusive takeUntil forwards that terminal frame and then cancels the interval.
            Flux<ServerSentEvent<String>> content = chatAgentService.streamChat(request);
            Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                    .map(i -> ServerSentEvent.<String>builder().comment("hb").build());
            // Observe-only: records the turn (analytics row + CHAT_TURN log) once it ends.
            // Its hooks swallow every failure and the DB write runs off the stream thread,
            // so it can't alter, delay or break what the shopper receives.
            StreamTurnRecorder recorder = new StreamTurnRecorder(shopConfig, request,
                    userAgent, xForwardedFor, xRealIp, referer);
            return content
                    .mergeWith(heartbeat)
                    .takeUntil(sse -> "done".equals(sse.event()) || "error".equals(sse.event()))
                    .doFinally(sig -> chatAgentService.clearShopContext())
                    .onErrorResume(e -> {
                        logger.error("Stream error for shop {}: {}", shop, e.getMessage());
                        chatAgentService.clearShopContext();
                        return Flux.just(sseError("Chat processing failed"));
                    })
                    .doOnNext(recorder::observe)
                    .doFinally(recorder::finish)
                    .contextWrite(ctx -> withVoucherContext(ctx, request, xForwardedFor, xRealIp))
                    .contextWrite(ctx -> ctx.put(ChatTurnMetrics.CONTEXT_KEY, recorder.metrics));
        } catch (Exception e) {
            logger.error("Error in streamMessage: {}", e.getMessage(), e);
            return Flux.just(sseError("Internal server error"));
        }
    }

    /**
     * Who is chatting, for the offer_discount tool (session limits, customer/IP signals).
     * Only these storefront endpoints set it, so no other chat surface can issue vouchers.
     * No session id means no context (the tool then refuses).
     */
    private Context withVoucherContext(Context ctx, ChatRequest request, String xForwardedFor, String xRealIp) {
        try {
            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.isBlank()) return ctx;
            return ctx.put(VoucherChatContext.CONTEXT_KEY, new VoucherChatContext(sessionId,
                request.getUserIdentifier(), extractClientIp(xForwardedFor, xRealIp)));
        } catch (Exception e) {
            return ctx;
        }
    }

    /** One CHAT_TURN log line per finished turn. Never throws. */
    private void logTurn(ChatTurnMetrics metrics, String outcome, long totalMs) {
        try {
            logger.info(metrics.toLogLine(outcome, totalMs));
        } catch (Exception e) {
            logger.debug("Could not log chat turn: {}", e.getMessage());
        }
    }

    /**
     * Watches the frames of one streamed turn and, when it ends, logs a CHAT_TURN
     * line and saves one ChatAnalytics row like the blocking path does. All hooks
     * catch everything; the DB write is fire-and-forget on boundedElastic.
     */
    private final class StreamTurnRecorder {
        final ChatTurnMetrics metrics = new ChatTurnMetrics("stream");
        private final long startTime = System.currentTimeMillis();
        private final ShopifyShop shopConfig;
        private final ChatRequest request;
        private final String userAgent;
        private final String xForwardedFor;
        private final String xRealIp;
        private final String referer;
        private final StringBuilder answer = new StringBuilder();
        private final AtomicBoolean finished = new AtomicBoolean();
        private volatile boolean sawDone;
        private volatile String errorMessage;

        StreamTurnRecorder(ShopifyShop shopConfig, ChatRequest request, String userAgent,
                           String xForwardedFor, String xRealIp, String referer) {
            this.shopConfig = shopConfig;
            this.request = request;
            this.userAgent = userAgent;
            this.xForwardedFor = xForwardedFor;
            this.xRealIp = xRealIp;
            this.referer = referer;
        }

        void observe(ServerSentEvent<String> sse) {
            try {
                String event = sse.event();
                if ("token".equals(event)) {
                    metrics.markFirstToken();
                    JsonNode data = objectMapper.readTree(sse.data());
                    synchronized (answer) {
                        answer.append(data.path("text").asText(""));
                    }
                } else if ("done".equals(event)) {
                    sawDone = true;
                } else if ("error".equals(event)) {
                    JsonNode data = objectMapper.readTree(sse.data());
                    errorMessage = data.path("message").asText("stream error");
                }
            } catch (Throwable t) {
                logger.debug("Stream recorder: could not observe frame: {}", t.getMessage());
            }
        }

        void finish(SignalType signal) {
            try {
                if (!finished.compareAndSet(false, true)) {
                    return;
                }
                long responseTime = System.currentTimeMillis() - startTime;
                boolean cancelled = signal == SignalType.CANCEL && !sawDone && errorMessage == null;
                String outcome = sawDone ? "ok" : cancelled ? "cancelled" : "error";
                logTurn(metrics, outcome, responseTime);

                String text;
                synchronized (answer) {
                    text = answer.toString();
                }
                Runnable save = () -> {
                    try {
                        if (sawDone || cancelled) {
                            // A disconnect still counts as answered, with what was streamed.
                            trackStreamedInteraction(text, responseTime,
                                cancelled ? "client disconnected" : null);
                        } else {
                            trackFailedInteractionWithShop(shopConfig, request,
                                "Stream failed: " + (errorMessage != null ? errorMessage : signal),
                                responseTime);
                        }
                    } catch (Throwable t) {
                        logger.error("Failed to track streamed chat: {}", t.getMessage());
                    }
                };
                Schedulers.boundedElastic().schedule(save);
            } catch (Throwable t) {
                logger.error("Stream recorder failed: {}", t.getMessage());
            }
        }

        private void trackStreamedInteraction(String text, long responseTime, String note) {
            boolean summed = metrics.hasUsage();
            ChatAnalytics analytics = buildSuccessfulInteraction(shopConfig, request, text, responseTime,
                summed ? metrics.getInputTokens() : null, summed ? metrics.getOutputTokens() : null,
                metrics.getModel(), metrics.getToolCount(), userAgent, xForwardedFor, xRealIp, referer);
            analytics.setErrorMessage(note);
            chatAnalyticsService.trackInteraction(analytics);
        }
    }

    private ServerSentEvent<String> sseError(String message) {
        return ServerSentEvent.<String>builder()
                .event("error")
                .data("{\"message\":\"" + message.replace("\"", "'") + "\"}")
                .build();
    }

    /**
     * Track successful chat interaction in analytics database
     * Includes token usage and API cost calculation
     */
    private void trackSuccessfulInteraction(ShopifyShop shop, ChatRequest request,
                                           String assistantResponse, long responseTimeMs,
                                           Integer inputTokens, Integer outputTokens,
                                           String modelUsed, int toolCount,
                                           String userAgent, String xForwardedFor,
                                           String xRealIp, String referer) {
        try {
            ChatAnalytics analytics = buildSuccessfulInteraction(shop, request, assistantResponse,
                responseTimeMs, inputTokens, outputTokens, modelUsed, toolCount,
                userAgent, xForwardedFor, xRealIp, referer);
            chatAnalyticsService.trackInteraction(analytics);
            logger.debug("Analytics tracked for session: {}", request.getSessionId());
        } catch (Exception e) {
            // Don't fail the request if analytics tracking fails
            logger.error("Failed to track analytics: {}", e.getMessage());
        }
    }

    /**
     * Build the analytics row for a successful turn (blocking or streamed).
     * modelUsed is the model the turn actually called (chatbot config can override
     * the shop's); falls back to the shop's model when unknown.
     */
    private ChatAnalytics buildSuccessfulInteraction(ShopifyShop shop, ChatRequest request,
                                                     String assistantResponse, long responseTimeMs,
                                                     Integer inputTokens, Integer outputTokens,
                                                     String modelUsed, int toolCount,
                                                     String userAgent, String xForwardedFor,
                                                     String xRealIp, String referer) {
        ChatAnalytics analytics = ChatAnalytics.builder()
            .shop(shop)
            .sessionId(request.getSessionId())
            .userIdentifier(request.getUserIdentifier())
            .userMessage(request.getMessage())
            .assistantResponse(assistantResponse)
            .modelUsed(modelUsed != null ? modelUsed : shop.getAiModel())
            .temperatureUsed(shop.getAiTemperature())
            .maxTokensUsed(shop.getAiMaxTokens())
            .responseTimeMs((int) responseTimeMs)
            .toolResultsCount(toolCount)
            .wasSuccessful(true)
            .userAgent(userAgent)
            .ipAddress(extractClientIp(xForwardedFor, xRealIp))
            .refererUrl(referer)
            .build();

        // Calculate and set token usage and API cost
        if (inputTokens != null && outputTokens != null) {
            analytics.setTokensUsed(inputTokens + outputTokens);
            analytics.calculateApiCost(inputTokens, outputTokens);
            logger.debug("Token usage - Input: {}, Output: {}, Total: {}, Cost: ${}",
                inputTokens, outputTokens, analytics.getTokensUsed(), analytics.getApiCostUsd());
        } else {
            logger.warn("Token usage not available from Claude API response");
        }
        return analytics;
    }

    /**
     * Track failed interaction (shop not found case)
     */
    private void trackFailedInteraction(String shopDomain, ChatRequest request,
                                       String errorMessage, long responseTimeMs) {
        try {
            // Try to get shop, but it might not exist
            if (shopifyShopService.isShopActive(shopDomain)) {
                ShopifyShop shop = shopifyShopService.getShop(shopDomain);
                trackFailedInteractionWithShop(shop, request, errorMessage, responseTimeMs);
            } else {
                logger.debug("Cannot track analytics for inactive shop: {}", shopDomain);
            }
        } catch (Exception e) {
            logger.error("Failed to track failed interaction: {}", e.getMessage());
        }
    }

    /**
     * Track failed interaction (with shop object)
     */
    private void trackFailedInteractionWithShop(ShopifyShop shop, ChatRequest request,
                                               String errorMessage, long responseTimeMs) {
        try {
            ChatAnalytics analytics = ChatAnalytics.builder()
                .shop(shop)
                .sessionId(request.getSessionId())
                .userIdentifier(request.getUserIdentifier())
                .userMessage(request.getMessage())
                .modelUsed(shop.getAiModel())
                .temperatureUsed(shop.getAiTemperature())
                .maxTokensUsed(shop.getAiMaxTokens())
                .responseTimeMs((int) responseTimeMs)
                .wasSuccessful(false)
                .errorMessage(errorMessage)
                .build();

            chatAnalyticsService.trackInteraction(analytics);
            logger.debug("Failed analytics tracked for session: {}", request.getSessionId());
        } catch (Exception e) {
            logger.error("Failed to track failed analytics: {}", e.getMessage());
        }
    }

    /**
     * Extract client IP address from request headers
     * Handles common proxy headers (X-Forwarded-For, X-Real-IP)
     */
    private String extractClientIp(String xForwardedFor, String xRealIp) {
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }

        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return "unknown";
    }
}
