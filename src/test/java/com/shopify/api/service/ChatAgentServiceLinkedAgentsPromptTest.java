package com.shopify.api.service;

import com.shopify.api.handler.tool.DelegateToAgentChatToolHandler;
import com.shopify.api.model.ChatbotConfig;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.model.SystemPrompt;
import com.shopify.api.model.agent.Agent;
import com.shopify.api.service.agent.AgentExecutionService;
import com.shopify.api.service.tool.AgentDelegationToolHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An unlinked agent (SPECIALS FINDER, id 19, unlinked 2026-10-19) must not be
 * advertised to the storefront bot: not in the generated prompt, not in the
 * DB-prompt path, not in the tool definitions.
 */
class ChatAgentServiceLinkedAgentsPromptTest {

    private StubClaude stub;
    private ChatAgentService service;
    private ChatbotConfig config;

    @BeforeEach
    void setUp() {
        stub = new StubClaude();
        Agent paint = Agent.builder().id(15L).name("PAINT EXPERT").description("Paint advice").isActive(true).build();
        Agent specials = Agent.builder().id(19L).name("SPECIALS FINDER").description("Finds deals").isActive(true).build();
        when(stub.agentRepository.findById(15L)).thenReturn(Optional.of(paint));
        when(stub.agentRepository.findById(19L)).thenReturn(Optional.of(specials));
        config = ChatbotConfig.builder().storeName("Hearns Hobbies").storeDescription("hobby shop")
            .linkedAgentIds(List.of(15L)).build();
        when(stub.configService.getConfig()).thenReturn(config);

        AgentDelegationToolHandler delegation =
            new AgentDelegationToolHandler(stub.agentRepository, mock(AgentExecutionService.class));
        service = stub.service(List.of(new DelegateToAgentChatToolHandler(delegation, stub.configService)));
    }

    @Test
    void configPromptListsOnlyLinkedAgents() {
        String prompt = service.getGeneratedSystemPrompt();

        assertThat(prompt).contains("- PAINT EXPERT: Paint advice");
        assertThat(prompt).doesNotContain("SPECIALS FINDER");
        assertThat(prompt).contains("These are the ONLY agents you can delegate to");
    }

    @Test
    void dbPromptPathGetsTheAuthoritativeLinkedList() {
        // A stored shop prompt written while SPECIALS FINDER was linked still names it.
        ShopifyShop shop = new ShopifyShop();
        shop.setShopDomain("hearnshobbies.myshopify.com");
        SystemPrompt stored = SystemPrompt.builder().promptName("storefront").version(3)
            .promptText("You are the Hearns assistant. For deals, delegate to SPECIALS FINDER.").build();
        when(stub.systemPromptService.getActivePromptByType(eq(SystemPrompt.PromptType.PRODUCT_SEARCH), any()))
            .thenReturn(Optional.of(stored));
        service.setShopContext(shop);

        String prompt = service.getGeneratedSystemPrompt();

        assertThat(prompt).startsWith("You are the Hearns assistant.");
        int section = prompt.indexOf("=== SPECIALIST AGENTS AVAILABLE ===");
        assertThat(section).isPositive();
        String appended = prompt.substring(section);
        assertThat(appended).contains("- PAINT EXPERT").doesNotContain("SPECIALS FINDER");
        assertThat(appended).contains("These are the ONLY agents you can delegate to");
    }

    @Test
    void noLinkedAgentsMeansNoAgentSection() {
        config.setLinkedAgentIds(List.of());

        assertThat(service.getGeneratedSystemPrompt()).doesNotContain("SPECIALIST AGENTS AVAILABLE");
    }

    @Test
    void toolDefinitionsNameNoAgents() {
        String tools = new ChatToolRegistry(List.of(new DelegateToAgentChatToolHandler(
            new AgentDelegationToolHandler(stub.agentRepository, mock(AgentExecutionService.class)),
            stub.configService))).buildToolDefinitions(config).toString();

        assertThat(tools).contains("delegate_to_agent");
        assertThat(tools).doesNotContain("SPECIALS FINDER").doesNotContain("\"enum\"");
    }
}
