package com.shopify.api.service;

import com.shopify.api.model.ChatbotConfig;
import com.shopify.api.handler.tool.ChatToolHandler;
import com.shopify.api.repository.agent.AgentRepository;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.mockito.Mockito.mock;

/**
 * Builds a real ChatAgentService whose Anthropic HTTP calls are answered from a
 * queue of canned bodies (JSON for blocking calls, SSE text for streamed ones).
 * No network, no DB.
 */
final class StubClaude {

    final Deque<String> responses = new ArrayDeque<>();
    final ChatbotConfigService configService = mock(ChatbotConfigService.class);
    final AgentRepository agentRepository = mock(AgentRepository.class);
    final SystemPromptService systemPromptService = mock(SystemPromptService.class);

    ChatAgentService service(List<ChatToolHandler> tools) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            String body = responses.poll();
            if (body == null) {
                return Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build());
            }
            String type = body.startsWith("event:") ? "text/event-stream" : "application/json";
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, type)
                    .body(body)
                    .build());
        });
        ChatAgentService service = new ChatAgentService(builder,
                mock(ProductService.class), configService, systemPromptService,
                mock(ModelValidationService.class), agentRepository,
                new ChatToolRegistry(tools), new DefaultResourceLoader(), "test-shop.myshopify.com");
        ReflectionTestUtils.setField(service, "anthropicApiKey", "test-key");
        ReflectionTestUtils.setField(service, "anthropicApiVersion", "2023-06-01");
        ReflectionTestUtils.setField(service, "anthropicModel", "claude-sonnet-5-5");
        ReflectionTestUtils.setField(service, "maxTokens", 1024);
        ReflectionTestUtils.setField(service, "temperature", 0.7);
        ReflectionTestUtils.setField(service, "effort", "low");
        ReflectionTestUtils.setField(service, "preorderTagsCsv", "preorder");
        ReflectionTestUtils.setField(service, "systemPromptFile", "classpath:prompts/system-prompt.txt");
        service.loadSystemPrompt();
        return service;
    }

    // ----- blocking (JSON) responses -----

    static String toolUseJson(String toolName, String inputJson) {
        return "{\"stop_reason\":\"tool_use\",\"content\":[{\"type\":\"tool_use\",\"id\":\"tu_1\",\"name\":\""
                + toolName + "\",\"input\":" + inputJson + "}],\"usage\":{\"input_tokens\":100,\"output_tokens\":20}}";
    }

    static String textJson(String text) {
        return "{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"" + text
                + "\"}],\"usage\":{\"input_tokens\":200,\"output_tokens\":30}}";
    }

    // ----- streamed (SSE) responses -----

    private static String sse(String event, String data) {
        return "event: " + event + "\ndata: " + data + "\n\n";
    }

    static String toolUseStream(String toolName, String inputJson, int in, int out) {
        String escaped = inputJson.replace("\\", "\\\\").replace("\"", "\\\"");
        return sse("message_start", "{\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":" + in
                        + ",\"cache_read_input_tokens\":7,\"output_tokens\":1}}}")
                + sse("content_block_start", "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":"
                        + "{\"type\":\"tool_use\",\"id\":\"tu_1\",\"name\":\"" + toolName + "\",\"input\":{}}}")
                + sse("content_block_delta", "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":"
                        + "{\"type\":\"input_json_delta\",\"partial_json\":\"" + escaped + "\"}}")
                + sse("content_block_stop", "{\"type\":\"content_block_stop\",\"index\":0}")
                + sse("message_delta", "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},"
                        + "\"usage\":{\"output_tokens\":" + out + "}}")
                + sse("message_stop", "{\"type\":\"message_stop\"}");
    }

    static String textStream(int in, int out, String... chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append(sse("message_start", "{\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":" + in
                + ",\"output_tokens\":1}}}"));
        sb.append(sse("content_block_start", "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":"
                + "{\"type\":\"text\",\"text\":\"\"}}"));
        for (String c : chunks) {
            sb.append(sse("content_block_delta", "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":"
                    + "{\"type\":\"text_delta\",\"text\":\"" + c + "\"}}"));
        }
        sb.append(sse("content_block_stop", "{\"type\":\"content_block_stop\",\"index\":0}"));
        sb.append(sse("message_delta", "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},"
                + "\"usage\":{\"output_tokens\":" + out + "}}"));
        sb.append(sse("message_stop", "{\"type\":\"message_stop\"}"));
        return sb.toString();
    }

    static ChatbotConfig config() {
        return ChatbotConfig.builder().storeName("Hearns Hobbies").build();
    }
}
