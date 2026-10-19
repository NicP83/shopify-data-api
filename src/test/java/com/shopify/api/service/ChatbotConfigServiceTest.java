package com.shopify.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopify.api.model.ChatbotConfig;
import com.shopify.api.model.ChatbotConfigEntity;
import com.shopify.api.repository.ChatbotConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Partial config saves must keep stored values (the 2026-10-18 wipe).
 * Request bodies go through Jackson exactly as the controller's @RequestBody does.
 */
class ChatbotConfigServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private ChatbotConfigRepository repository;
    private ChatbotConfigService service;
    private ChatbotConfigEntity stored;

    @BeforeEach
    void setUp() {
        repository = mock(ChatbotConfigRepository.class);
        service = new ChatbotConfigService(repository);
        stored = storedEntity();
        when(repository.findGlobalConfig()).thenReturn(Optional.of(stored));
        when(repository.save(any(ChatbotConfigEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** A non-default live-like config, so any reset to false/0 shows up. */
    private static ChatbotConfigEntity storedEntity() {
        ChatbotConfigEntity e = new ChatbotConfigEntity();
        e.setId(1L);
        e.setStoreName("Hearns Hobbies");
        e.setModelName("claude-sonnet-4-6");
        e.setTemperature(BigDecimal.valueOf(0.7));
        e.setMaxTokens(4096);
        e.setRequireSearchBeforeRecommendation(true);
        e.setEnableProductSearch(true);
        e.setMaxSearchResults(10);
        e.setIncludeCartLinks(true);
        e.setIncludeProductLinks(true);
        e.setShowPrices(true);
        e.setShowSkus(true);
        e.setLinkedAgentIds(List.of(5L, 6L));
        e.setLinkedWorkflowIds(List.of(9L));
        return e;
    }

    private ChatbotConfig body(String json) throws Exception {
        return mapper.readValue(json, ChatbotConfig.class);
    }

    @Test
    void partialPutKeepsEveryOtherStoredValue() throws Exception {
        ChatbotConfig saved = service.updateConfig(body("{\"modelName\":\"claude-sonnet-5-5\"}"));

        assertThat(saved.getModelName()).isEqualTo("claude-sonnet-5-5");
        assertThat(saved.isRequireSearchBeforeRecommendation()).isTrue();
        assertThat(saved.isEnableProductSearch()).isTrue();
        assertThat(saved.getMaxSearchResults()).isEqualTo(10);
        assertThat(saved.isIncludeCartLinks()).isTrue();
        assertThat(saved.isIncludeProductLinks()).isTrue();
        assertThat(saved.isShowPrices()).isTrue();
        assertThat(saved.isShowSkus()).isTrue();
        assertThat(saved.getStoreName()).isEqualTo("Hearns Hobbies");
        assertThat(saved.getTemperature()).isEqualTo(0.7);
        assertThat(saved.getMaxTokens()).isEqualTo(4096);
        assertThat(saved.getLinkedAgentIds()).containsExactly(5L, 6L);
        assertThat(saved.getLinkedWorkflowIds()).containsExactly(9L);
    }

    @Test
    void explicitFalseAndNumbersAreStillSaved() throws Exception {
        ChatbotConfig saved = service.updateConfig(body(
            "{\"showSkus\":false,\"includeCartLinks\":false,\"maxSearchResults\":3}"));

        assertThat(saved.isShowSkus()).isFalse();
        assertThat(saved.isIncludeCartLinks()).isFalse();
        assertThat(saved.getMaxSearchResults()).isEqualTo(3);
        assertThat(saved.isShowPrices()).isTrue();
        assertThat(saved.isEnableProductSearch()).isTrue();
    }

    @Test
    void fullBodyLikeTheSettingsPageSavesEveryField() throws Exception {
        // The Settings page GETs the config, edits it, and PUTs the whole object back.
        ChatbotConfig full = stored.toConfig();
        full.setEnableProductSearch(false);
        full.setRequireSearchBeforeRecommendation(false);
        full.setMaxSearchResults(7);
        full.setIncludeCartLinks(false);
        full.setIncludeProductLinks(false);
        full.setShowPrices(false);
        full.setShowSkus(false);
        full.setModelName("claude-sonnet-5-5");
        String json = mapper.writeValueAsString(full);

        ChatbotConfig saved = service.updateConfig(body(json));

        assertThat(saved.isEnableProductSearch()).isFalse();
        assertThat(saved.isRequireSearchBeforeRecommendation()).isFalse();
        assertThat(saved.getMaxSearchResults()).isEqualTo(7);
        assertThat(saved.isIncludeCartLinks()).isFalse();
        assertThat(saved.isIncludeProductLinks()).isFalse();
        assertThat(saved.isShowPrices()).isFalse();
        assertThat(saved.isShowSkus()).isFalse();
        assertThat(saved.getModelName()).isEqualTo("claude-sonnet-5-5");
    }

    @Test
    void zeroSearchResultsIsRejectedAndNothingSaved() {
        assertThatThrownBy(() -> service.updateConfig(body("{\"maxSearchResults\":0}")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxSearchResults");
        verify(repository, never()).save(any());
        assertThat(stored.getMaxSearchResults()).isEqualTo(10);
    }

    @Test
    void partialProfileSaveKeepsStoredValues() throws Exception {
        ChatbotConfigEntity profile = storedEntity();
        profile.setId(2L);
        profile.setSlug("paint");
        when(repository.findBySlug("paint")).thenReturn(Optional.of(profile));

        ChatbotConfig saved = service.saveProfile(body("{\"slug\":\"paint\",\"displayName\":\"Paint\"}"));

        assertThat(saved.getMaxSearchResults()).isEqualTo(10);
        assertThat(saved.isShowPrices()).isTrue();
        assertThat(saved.isIncludeCartLinks()).isTrue();
        assertThat(saved.getDisplayName()).isEqualTo("Paint");
    }

    @Test
    void getJsonShapeIsUnchanged() throws Exception {
        // Unset fields still serialize as the old primitive defaults, never null.
        JsonNode json = mapper.valueToTree(new ChatbotConfig());
        assertThat(json.get("enableProductSearch").isBoolean()).isTrue();
        assertThat(json.get("enableProductSearch").asBoolean()).isFalse();
        assertThat(json.get("requireSearchBeforeRecommendation").asBoolean()).isFalse();
        assertThat(json.get("maxSearchResults").isInt()).isTrue();
        assertThat(json.get("maxSearchResults").asInt()).isZero();
        assertThat(json.get("includeCartLinks").asBoolean()).isFalse();
        assertThat(json.get("includeProductLinks").asBoolean()).isTrue();
        assertThat(json.get("showPrices").asBoolean()).isFalse();
        assertThat(json.get("showSkus").asBoolean()).isFalse();
        // The raw *IfSet() accessors are not exposed as properties.
        assertThat(json.fieldNames()).toIterable().noneMatch(n -> n.toLowerCase().contains("ifset"));
    }
}
