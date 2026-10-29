package com.shopify.api.controller;

import com.shopify.api.filter.AdminAuthFilter;
import com.shopify.api.model.ApiKey;
import com.shopify.api.model.voucher.VoucherOverride;
import com.shopify.api.model.voucher.VoucherSettings;
import com.shopify.api.repository.voucher.VoucherLedgerRepository;
import com.shopify.api.service.ApiKeyService;
import com.shopify.api.service.voucher.VoucherAuthGuard;
import com.shopify.api.service.voucher.VoucherClock;
import com.shopify.api.service.voucher.VoucherOverrideService;
import com.shopify.api.service.voucher.VoucherReconcileService;
import com.shopify.api.service.voucher.VoucherReportService;
import com.shopify.api.service.voucher.VoucherScopeMonitor;
import com.shopify.api.service.voucher.VoucherSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The owner command needs the admin key AND the owner key, even with the admin
 * filter in REPORT mode (which on its own lets unauthenticated requests through).
 */
class VoucherAdminControllerTest {

    static final String ADMIN = "hh_admin_key";
    static final String NON_ADMIN = "hh_hr_key";
    static final String OWNER = "owner-key-0123456789-abcdefghijklmnop";

    ApiKeyService apiKeys;
    VoucherOverrideService overrides;
    VoucherSettingsService settings;
    VoucherScopeMonitor scope;
    MockMvc mvc;
    VoucherSettings stored;

    @BeforeEach
    void setUp() {
        apiKeys = mock(ApiKeyService.class);
        when(apiKeys.validate(anyString())).thenReturn(Optional.empty());
        when(apiKeys.validate(ADMIN)).thenReturn(Optional.of(
            ApiKey.builder().id(1L).name("nic-admin").scopes("admin").active(true).build()));
        when(apiKeys.validate(NON_ADMIN)).thenReturn(Optional.of(
            ApiKey.builder().id(2L).name("hr").scopes("agents:run").active(true).build()));
        mvc = build(OWNER);
    }

    MockMvc build(String ownerKey) {
        overrides = mock(VoucherOverrideService.class);
        settings = mock(VoucherSettingsService.class);
        scope = mock(VoucherScopeMonitor.class);
        stored = VoucherSettings.defaults();
        when(settings.currentStrict()).thenReturn(stored);
        when(settings.save(any())).thenAnswer(i -> i.getArgument(0));
        when(overrides.create(any(), anyString(), any())).thenAnswer(i -> VoucherOverride.builder().id(9L)
            .type(VoucherOverride.Type.POOL_RAISE).value(new BigDecimal("20")).reason("r").createdBy(i.getArgument(1))
            .createdAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build());
        when(overrides.recent()).thenReturn(List.of());
        VoucherAuthGuard guard = new VoucherAuthGuard(apiKeys, null, ownerKey);
        VoucherAdminController controller = new VoucherAdminController(guard, settings, overrides,
            mock(VoucherReportService.class), mock(VoucherReconcileService.class), scope,
            mock(VoucherLedgerRepository.class), new VoucherClock(), true);
        return MockMvcBuilders.standaloneSetup(controller)
            .addFilters(new AdminAuthFilter(apiKeys, null, AdminAuthFilter.Mode.REPORT))
            .build();
    }

    static MockHttpServletRequestBuilder override(String adminKey, String ownerKey) {
        MockHttpServletRequestBuilder b = post("/api/vouchers/override").contentType(MediaType.APPLICATION_JSON)
            .content("{\"type\":\"POOL_RAISE\",\"value\":20,\"who\":\"Nic\",\"reason\":\"Sale weekend\"}");
        if (adminKey != null) b.header("X-API-Key", adminKey);
        if (ownerKey != null) b.header("X-Owner-Key", ownerKey);
        return b;
    }

    @Test
    void overrideWithBothKeysIsAcceptedAndAudited() throws Exception {
        mvc.perform(override(ADMIN, OWNER)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(9));
        verify(overrides).create(any(), org.mockito.ArgumentMatchers.eq("Nic (key:nic-admin)"), any());
    }

    @Test
    void overrideNeedsTheAdminKeyEvenInReportMode() throws Exception {
        mvc.perform(override(null, OWNER)).andExpect(status().isUnauthorized());
        mvc.perform(override(NON_ADMIN, OWNER)).andExpect(status().isUnauthorized());
        verify(overrides, never()).create(any(), anyString(), any());
    }

    @Test
    void overrideNeedsTheOwnerKey() throws Exception {
        mvc.perform(override(ADMIN, null)).andExpect(status().isForbidden());
        mvc.perform(override(ADMIN, OWNER + "x")).andExpect(status().isForbidden());
        mvc.perform(override(ADMIN, ADMIN)).andExpect(status().isForbidden());
        verify(overrides, never()).create(any(), anyString(), any());
    }

    @Test
    void ownerKeyUnsetOrShortDisablesOverrides() throws Exception {
        MockMvc unset = build("");
        unset.perform(override(ADMIN, "")).andExpect(status().isServiceUnavailable());
        MockMvc shortKey = build("short");
        shortKey.perform(override(ADMIN, "short")).andExpect(status().isServiceUnavailable());
        verify(overrides, never()).create(any(), anyString(), any());
    }

    @Test
    void repeatedWrongOwnerKeysLockTheEndpoint() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(override(ADMIN, "guess-" + i)).andExpect(status().isForbidden());
        }
        mvc.perform(override(ADMIN, OWNER)).andExpect(status().isTooManyRequests());
        verify(overrides, never()).create(any(), anyString(), any());
    }

    @Test
    void readEndpointsNeedTheAdminKeyInReportMode() throws Exception {
        mvc.perform(get("/api/vouchers/settings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/vouchers/ledger")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/vouchers/report")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/vouchers/settings").header("X-API-Key", ADMIN)).andExpect(status().isOk());
    }

    @Test
    void enablingOrLooseningNeedsTheOwnerKey() throws Exception {
        mvc.perform(put("/api/vouchers/settings").header("X-API-Key", ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.needs_owner_key[0]").value("enabled"));
        mvc.perform(put("/api/vouchers/settings").header("X-API-Key", ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"dailyPool\":200}"))
            .andExpect(status().isForbidden());
        verify(settings, never()).save(any());

        mvc.perform(put("/api/vouchers/settings").header("X-API-Key", ADMIN).header("X-Owner-Key", OWNER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"dailyPool\":80}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.updatedBy").value("nic-admin +owner"));
    }

    @Test
    void ownerCannotExceedHardCeilings() throws Exception {
        mvc.perform(put("/api/vouchers/settings").header("X-API-Key", ADMIN).header("X-Owner-Key", OWNER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"dailyPool\":5000}"))
            .andExpect(status().isBadRequest());
        verify(settings, never()).save(any());
    }

    @Test
    void unreadableSettingsRefuseUpdatesInsteadOfComparingWithDefaults() throws Exception {
        when(settings.currentStrict()).thenThrow(
            new VoucherSettingsService.SettingsUnavailableException("db down", null));
        mvc.perform(put("/api/vouchers/settings").header("X-API-Key", ADMIN).header("X-Owner-Key", OWNER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"dailyPool\":20}"))
            .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/vouchers/settings").header("X-API-Key", ADMIN)).andExpect(status().isServiceUnavailable());
        verify(settings, never()).save(any());
    }

    @Test
    void staleSaveIsAConflict() throws Exception {
        when(settings.save(any())).thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(
            VoucherSettings.class, 1L));
        mvc.perform(put("/api/vouchers/settings").header("X-API-Key", ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"dailyPool\":20,\"version\":3}"))
            .andExpect(status().isConflict());
    }

    @Test
    void tighteningAndKillSwitchNeedOnlyTheAdminKey() throws Exception {
        mvc.perform(put("/api/vouchers/settings").header("X-API-Key", ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"dailyPool\":20,\"exclusions\":{\"vendors\":[\"Bandai\"]}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dailyPool").value(20))
            .andExpect(jsonPath("$.exclusions.vendors[0]").value("Bandai"));

        when(settings.disable("nic-admin")).thenReturn(stored);
        mvc.perform(post("/api/vouchers/kill").header("X-API-Key", ADMIN)).andExpect(status().isOk());
        verify(settings).disable("nic-admin");
        mvc.perform(post("/api/vouchers/kill")).andExpect(status().isUnauthorized());
    }
}
