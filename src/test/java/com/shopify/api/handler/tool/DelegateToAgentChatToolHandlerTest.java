package com.shopify.api.handler.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.model.ChatbotConfig;
import com.shopify.api.model.agent.Agent;
import com.shopify.api.repository.agent.AgentRepository;
import com.shopify.api.service.ChatbotConfigService;
import com.shopify.api.service.agent.AgentExecutionService;
import com.shopify.api.service.tool.AgentDelegationToolHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The storefront bot may only delegate to agents linked in its config:
 * agent 23 (Product Enrichment) must be unreachable unless linked.
 */
class DelegateToAgentChatToolHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private AgentRepository agentRepository;
    private AgentExecutionService executionService;
    private ChatbotConfigService configService;
    private AgentDelegationToolHandler delegationHandler;
    private DelegateToAgentChatToolHandler chatHandler;

    @BeforeEach
    void setUp() {
        agentRepository = mock(AgentRepository.class);
        executionService = mock(AgentExecutionService.class);
        configService = mock(ChatbotConfigService.class);
        delegationHandler = new AgentDelegationToolHandler(agentRepository, executionService);
        chatHandler = new DelegateToAgentChatToolHandler(delegationHandler, configService);

        stubAgent(5L, "PAINT EXPERT");
        stubAgent(23L, "Product Enrichment");
        ObjectNode output = mapper.createObjectNode().put("text", "Use Tamiya XF-1");
        when(executionService.executeAgentForDelegation(anyLong(), any()))
            .thenReturn(Mono.just(AgentExecutionService.AgentExecutionResult.builder()
                .output(output).inputTokens(10).outputTokens(5).success(true).build()));
    }

    private void stubAgent(long id, String name) {
        Agent agent = Agent.builder().id(id).name(name).isActive(true).build();
        when(agentRepository.findByName(name)).thenReturn(Optional.of(agent));
        when(agentRepository.findAllById(List.of(id))).thenReturn(List.of(agent));
    }

    private JsonNode input(String agentName) {
        return mapper.createObjectNode().put("agent_name", agentName).put("task", "help");
    }

    private static ChatbotConfig linked(Long... ids) {
        return ChatbotConfig.builder().linkedAgentIds(List.of(ids)).build();
    }

    private JsonNode run(String agentName, ChatbotConfig contextConfig) throws Exception {
        Mono<String> result = chatHandler.execute(input(agentName));
        if (contextConfig != null) {
            result = result.contextWrite(ctx ->
                ctx.put(DelegateToAgentChatToolHandler.CHAT_CONFIG_CONTEXT_KEY, contextConfig));
        }
        return mapper.readTree(result.block());
    }

    @Test
    void unlinkedAgentIsRefusedAndNeverRuns() throws Exception {
        when(configService.getConfig()).thenReturn(linked(5L));

        JsonNode result = run("Product Enrichment", null);

        assertThat(result.get("success").asBoolean()).isFalse();
        assertThat(result.get("error").asText()).contains("not available from chat");
        verify(executionService, never()).executeAgentForDelegation(eq(23L), any());
    }

    @Test
    void unlinkedSpecialsFinderIsRefusedWithTheAllowedAgentsNamed() throws Exception {
        // Production 2026-10-19: agent 19 was unlinked (linked = [15,...]) but still ran.
        stubAgent(19L, "SPECIALS FINDER");
        when(configService.getConfig()).thenReturn(linked(5L));

        JsonNode result = run("SPECIALS FINDER", null);

        assertThat(result.get("success").asBoolean()).isFalse();
        assertThat(result.get("error").asText())
            .contains("not available from chat")
            .contains("Available agents: PAINT EXPERT");
        verify(executionService, never()).executeAgentForDelegation(eq(19L), any());
    }

    @Test
    void linkedAgentStillRuns() throws Exception {
        when(configService.getConfig()).thenReturn(linked(5L));

        JsonNode result = run("PAINT EXPERT", null);

        assertThat(result.get("success").asBoolean()).isTrue();
        assertThat(result.get("response").asText()).isEqualTo("Use Tamiya XF-1");
        verify(executionService).executeAgentForDelegation(eq(5L), any());
    }

    @Test
    void usesTheTurnsOwnConfigFromContext() throws Exception {
        // Global links only 5; this persona turn links 23, so 23 is allowed here...
        when(configService.getConfig()).thenReturn(linked(5L));
        assertThat(run("Product Enrichment", linked(23L)).get("success").asBoolean()).isTrue();
        // ...and 5 is not, because the persona didn't link it.
        assertThat(run("PAINT EXPERT", linked(23L)).get("success").asBoolean()).isFalse();
        verify(executionService, never()).executeAgentForDelegation(eq(5L), any());
    }

    @Test
    void noLinkedAgentsMeansNothingIsAllowed() throws Exception {
        when(configService.getConfig()).thenReturn(new ChatbotConfig());

        assertThat(run("PAINT EXPERT", null).get("success").asBoolean()).isFalse();
        verify(executionService, never()).executeAgentForDelegation(anyLong(), any());
    }

    @Test
    void dbAgentToolPathIsUnrestricted() {
        // ToolHandler.execute(input) (tools table path) keeps its old behaviour.
        JsonNode result = delegationHandler.execute(input("Product Enrichment")).block();
        assertThat(result.get("success").asBoolean()).isTrue();
        verify(executionService).executeAgentForDelegation(eq(23L), any());
    }
}
