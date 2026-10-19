package com.shopify.api.service;

import com.shopify.api.config.CacheConfig;
import com.shopify.api.model.ChatbotConfig;
import com.shopify.api.model.ChatbotConfigEntity;
import com.shopify.api.repository.ChatbotConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs the real Spring cache proxy so self-invocation would show up:
 * after a save, every cached view of the config must be refreshed.
 */
@SpringJUnitConfig(ChatbotConfigServiceCacheTest.TestConfig.class)
@TestPropertySource(properties = {
    "chatbot.store.name=x", "chatbot.store.description=x", "chatbot.store.categories=x",
    "chatbot.behavior.scope-instructions=x", "chatbot.behavior.out-of-scope-response=x",
    "chatbot.behavior.require-search=true", "chatbot.tools.enable-product-search=true",
    "chatbot.tools.max-search-results=5", "chatbot.response.tone=friendly",
    "chatbot.response.include-cart-links=true", "chatbot.response.include-product-links=true",
    "chatbot.response.show-prices=true", "chatbot.response.show-skus=false",
    "chatbot.custom-instructions=x"
})
class ChatbotConfigServiceCacheTest {

    @Configuration
    @Import(CacheConfig.class)
    static class TestConfig {
        @Bean
        ChatbotConfigRepository chatbotConfigRepository() {
            return mock(ChatbotConfigRepository.class);
        }

        @Bean
        ChatbotConfigService chatbotConfigService(ChatbotConfigRepository repository) {
            return new ChatbotConfigService(repository);
        }
    }

    @Autowired
    ChatbotConfigService service;

    @Autowired
    ChatbotConfigRepository repository;

    @Test
    void saveRefreshesGlobalAndPersonaFallbackCaches() {
        ChatbotConfigEntity entity = new ChatbotConfigEntity();
        entity.setId(1L);
        entity.setModelName("claude-sonnet-4-6");
        entity.setMaxSearchResults(10);
        when(repository.findGlobalConfig()).thenReturn(Optional.of(entity));
        when(repository.findBySlug(any())).thenReturn(Optional.empty());
        when(repository.save(any(ChatbotConfigEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // Warm both caches: global, and a persona slug that falls back to global.
        assertThat(service.getConfig().getModelName()).isEqualTo("claude-sonnet-4-6");
        assertThat(service.getConfigBySlug("missing").getModelName()).isEqualTo("claude-sonnet-4-6");

        ChatbotConfig update = new ChatbotConfig();
        update.setModelName("claude-sonnet-5-5");
        service.updateConfig(update);

        assertThat(service.getConfig().getModelName()).isEqualTo("claude-sonnet-5-5");
        assertThat(service.getConfigBySlug("missing").getModelName()).isEqualTo("claude-sonnet-5-5");
        assertThat(service.getConfig().getMaxSearchResults()).isEqualTo(10);
    }
}
