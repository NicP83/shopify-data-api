package com.shopify.api.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.shopify.api.client.MCPClient;
import com.shopify.api.model.agent.Agent;
import com.shopify.api.model.agent.AgentExecution;
import com.shopify.api.model.agent.AgentTool;
import com.shopify.api.model.agent.Tool;
import com.shopify.api.repository.agent.AgentExecutionRepository;
import com.shopify.api.repository.agent.AgentRepository;
import com.shopify.api.repository.agent.AgentToolRepository;
import com.shopify.api.service.ModelValidationService;
import com.shopify.api.service.agent.AgentExecutionService.AgentExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The native web_search tool (BUILTIN row) is sent verbatim to Anthropic, and if
 * Anthropic rejects it the agent retries once without server tools.
 */
class AgentExecutionServiceWebSearchTest {

    private static final String END_TURN =
            "{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"Tamiya X-1 is a gloss black acrylic.\"}],"
            + "\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<JsonNode> sentBodies = Collections.synchronizedList(new ArrayList<>());
    private final AtomicReference<List<ClientResponse>> responses = new AtomicReference<>();
    private AgentExecutionService service;
    private Agent agent;

    @BeforeEach
    void setUp() throws Exception {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            sentBodies.add(captureBody(request));
            return Mono.just(responses.get().remove(0));
        });

        agent = Agent.builder().id(15L).name("PAINT AND MODELING TOOLS EXPERT")
                .modelProvider("anthropic").modelName("claude-haiku-4-5-20251001")
                .systemPrompt("You are a paint expert.").temperature(new BigDecimal("0.7")).maxTokens(1024)
                .build();

        Tool webSearch = Tool.builder().id(1L).name("web_search").type("BUILTIN").isActive(true)
                .handlerClass("native:web_search_20250305")
                .inputSchemaJson(objectMapper.readTree("{\"type\":\"web_search_20250305\",\"name\":\"web_search\",\"max_uses\":5}"))
                .build();
        Tool catalog = Tool.builder().id(2L).name("lookup_catalog_product").type("AGENT").isActive(true)
                .description("Catalog lookup").handlerClass("com.shopify.api.service.tool.ProductSearchToolHandler")
                .inputSchemaJson(objectMapper.readTree("{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}"))
                .build();

        AgentToolRepository agentToolRepository = mock(AgentToolRepository.class);
        when(agentToolRepository.findByAgentIdWithTool(anyLong())).thenReturn(List.of(
                AgentTool.builder().agent(agent).tool(webSearch).build(),
                AgentTool.builder().agent(agent).tool(catalog).build()));

        ModelValidationService modelValidation = mock(ModelValidationService.class);
        when(modelValidation.validateOrDefault(anyString())).thenAnswer(inv -> inv.getArgument(0));

        service = new AgentExecutionService(mock(AgentRepository.class), agentToolRepository,
                mock(AgentExecutionRepository.class), builder, objectMapper, mock(MCPClient.class),
                modelValidation, mock(ToolRegistryService.class), mock(ApplicationContext.class));
        ReflectionTestUtils.setField(service, "anthropicApiKey", "test-key");
        ReflectionTestUtils.setField(service, "anthropicApiVersion", "2023-06-01");
        ReflectionTestUtils.setField(service, "maxAgentIterations", 6);
    }

    private JsonNode captureBody(ClientRequest request) {
        MockClientHttpRequest mock = new MockClientHttpRequest(request.method(), request.url());
        @SuppressWarnings("unchecked")
        BodyInserter<Object, ClientHttpRequest> inserter = (BodyInserter<Object, ClientHttpRequest>) request.body();
        inserter.insert(mock, new BodyInserter.Context() {
            @Override
            public List<org.springframework.http.codec.HttpMessageWriter<?>> messageWriters() {
                return org.springframework.web.reactive.function.client.ExchangeStrategies.withDefaults().messageWriters();
            }

            @Override
            public java.util.Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest() {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Map<String, Object> hints() {
                return java.util.Map.of();
            }
        }).block();
        try {
            return objectMapper.readTree(mock.getBodyAsString().block());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ClientResponse json(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private AgentExecutionResult run() throws Exception {
        Mono<AgentExecutionResult> result = ReflectionTestUtils.invokeMethod(service, "executeWithClaude",
                agent, objectMapper.readTree("{\"task\":\"What is Tamiya X-1?\"}"),
                AgentExecution.builder().id(99L).build(), null);
        return result.block();
    }

    private static List<String> toolNames(JsonNode body) {
        List<String> names = new ArrayList<>();
        body.path("tools").forEach(t -> names.add(t.path("name").asText()));
        return names;
    }

    @Test
    void builtinWebSearchIsSentAsNativeServerTool() throws Exception {
        responses.set(new ArrayList<>(List.of(json(HttpStatus.OK, END_TURN))));

        AgentExecutionResult result = run();

        assertThat(result.getOutput().get("text").asText()).contains("gloss black");
        assertThat(sentBodies).hasSize(1);
        JsonNode webSearch = sentBodies.get(0).get("tools").get(0);
        assertThat(webSearch.get("type").asText()).isEqualTo("web_search_20250305");
        assertThat(webSearch.has("input_schema")).isFalse();
        assertThat(toolNames(sentBodies.get(0))).containsExactly("web_search", "lookup_catalog_product");
    }

    @Test
    void rejectedWebSearchRetriesOnceWithoutServerTools() throws Exception {
        responses.set(new ArrayList<>(List.of(
                json(HttpStatus.BAD_REQUEST, "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\","
                        + "\"message\":\"web_search is not enabled for this organization\"}}"),
                json(HttpStatus.OK, END_TURN))));

        AgentExecutionResult result = run();

        assertThat(result.getOutput().get("text").asText()).contains("gloss black");
        assertThat(sentBodies).hasSize(2);
        assertThat(toolNames(sentBodies.get(0))).contains("web_search");
        assertThat(toolNames(sentBodies.get(1))).containsExactly("lookup_catalog_product");
    }

    @Test
    void otherBadRequestsStillFail() {
        responses.set(new ArrayList<>(List.of(
                json(HttpStatus.BAD_REQUEST, "{\"type\":\"error\",\"error\":{\"message\":\"max_tokens too large\"}}"))));

        assertThatThrownBy(this::run).isInstanceOf(WebClientResponseException.BadRequest.class);
        assertThat(sentBodies).hasSize(1);
    }

    @Test
    void removeServerToolsKeepsClientTools() throws Exception {
        ArrayNode tools = (ArrayNode) objectMapper.readTree(
                "[{\"type\":\"web_search_20250305\",\"name\":\"web_search\"},{\"name\":\"lookup\",\"input_schema\":{}}]");

        assertThat(AgentExecutionService.removeServerTools(tools)).isTrue();
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).get("name").asText()).isEqualTo("lookup");
        assertThat(AgentExecutionService.removeServerTools(tools)).isFalse();
    }
}
