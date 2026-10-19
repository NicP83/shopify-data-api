package com.shopify.api.service;

import reactor.util.context.ContextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-turn measurements for one storefront chat turn (blocking or streamed).
 * The controller creates one, puts it in the Reactor context, and logs it as a
 * single CHAT_TURN line when the turn ends; ChatAgentService adds Claude usage
 * and tool timings to it as they happen. Everything here is best-effort
 * observation: callers must never let it affect the reply.
 */
public class ChatTurnMetrics {

    /** Reactor context key under which the current turn's metrics travel. */
    public static final String CONTEXT_KEY = "chat.turnMetrics";

    private final String path;
    private final long startNanos = System.nanoTime();
    private final AtomicInteger claudeCalls = new AtomicInteger();
    private final AtomicLong claudeMs = new AtomicLong();
    private final AtomicInteger inputTokens = new AtomicInteger();
    private final AtomicInteger outputTokens = new AtomicInteger();
    private final AtomicInteger cacheReadTokens = new AtomicInteger();
    private final AtomicInteger cacheWriteTokens = new AtomicInteger();
    private final AtomicLong firstTokenMs = new AtomicLong(-1);
    private final List<String> toolTimings = new ArrayList<>();
    private volatile String model;
    private volatile boolean usageSeen;

    public ChatTurnMetrics(String path) {
        this.path = path;
    }

    /** The metrics in this Reactor context, or null when none were put there. */
    public static ChatTurnMetrics from(ContextView ctx) {
        return ctx.<ChatTurnMetrics>getOrEmpty(CONTEXT_KEY).orElse(null);
    }

    public long elapsedMs() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    public void recordClaudeCall(String model, long durationMs, int input, int output,
                                 int cacheRead, int cacheWrite) {
        if (model != null) {
            this.model = model;
        }
        claudeCalls.incrementAndGet();
        claudeMs.addAndGet(durationMs);
        inputTokens.addAndGet(input);
        outputTokens.addAndGet(output);
        cacheReadTokens.addAndGet(cacheRead);
        cacheWriteTokens.addAndGet(cacheWrite);
        usageSeen = true;
    }

    public void recordTool(String toolName, long durationMs, boolean ok) {
        synchronized (toolTimings) {
            toolTimings.add(toolName + ":" + durationMs + "ms" + (ok ? "" : "(failed)"));
        }
    }

    /** Marks time to first answer token; only the first call counts. */
    public void markFirstToken() {
        firstTokenMs.compareAndSet(-1, elapsedMs());
    }

    public String getModel() {
        return model;
    }

    /** True once at least one Claude call reported token usage. */
    public boolean hasUsage() {
        return usageSeen;
    }

    /**
     * Uncached input tokens summed over every Claude call of the turn (Anthropic's
     * input_tokens, which excludes cache reads/writes, as the blocking path records).
     */
    public int getInputTokens() {
        return inputTokens.get();
    }

    public int getOutputTokens() {
        return outputTokens.get();
    }

    public int getToolCount() {
        synchronized (toolTimings) {
            return toolTimings.size();
        }
    }

    public String toLogLine(String outcome, long totalMs) {
        String tools;
        synchronized (toolTimings) {
            tools = String.join(",", toolTimings);
        }
        long ttft = firstTokenMs.get();
        return "CHAT_TURN path=" + path
                + " outcome=" + outcome
                + " totalMs=" + totalMs
                + " ttftMs=" + (ttft < 0 ? "-" : String.valueOf(ttft))
                + " claudeCalls=" + claudeCalls.get()
                + " claudeMs=" + claudeMs.get()
                + " tools=[" + tools + "]"
                + " inTokens=" + inputTokens.get()
                + " outTokens=" + outputTokens.get()
                + " cacheRead=" + cacheReadTokens.get()
                + " cacheWrite=" + cacheWriteTokens.get()
                + " model=" + model;
    }
}
