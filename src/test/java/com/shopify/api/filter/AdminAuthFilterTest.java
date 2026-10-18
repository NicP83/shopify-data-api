package com.shopify.api.filter;

import com.shopify.api.filter.AdminAuthFilter.Mode;
import com.shopify.api.model.ApiKey;
import com.shopify.api.service.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuthFilterTest {

    private static final String BOOTSTRAP = "bootstrap-secret";
    private static final String ADMIN_KEY = "hh_admin";
    private static final String HR_KEY = "hh_hr";
    private static final String WF_KEY = "hh_wf";

    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyService = mock(ApiKeyService.class);
        when(apiKeyService.validate(anyString())).thenReturn(Optional.empty());
        when(apiKeyService.validate(ADMIN_KEY)).thenReturn(Optional.of(key(1L, "admin")));
        when(apiKeyService.validate(HR_KEY)).thenReturn(Optional.of(key(2L, "agents:run")));
        when(apiKeyService.validate(WF_KEY)).thenReturn(Optional.of(key(3L, "workflows:run")));
    }

    private static ApiKey key(Long id, String scopes) {
        return ApiKey.builder().id(id).name("k" + id).scopes(scopes).active(true).build();
    }

    /** Result of one request through the filter: whether it reached the controller, and the status. */
    private record Outcome(boolean passed, int status) {}

    private Outcome run(Mode mode, String method, String path, String apiKey) throws Exception {
        return run(mode, method, path, path, apiKey);
    }

    private Outcome run(Mode mode, String method, String servletPath, String requestUri, String apiKey)
            throws Exception {
        AdminAuthFilter filter = new AdminAuthFilter(apiKeyService, BOOTSTRAP, mode);
        MockHttpServletRequest request = new MockHttpServletRequest(method, requestUri);
        request.setServletPath(servletPath);
        if (apiKey != null) request.addHeader("X-API-Key", apiKey);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Outcome(chain.getRequest() != null, response.getStatus());
    }

    // ---- public routes stay open even when enforcing ----

    @Test
    void storefrontChatIsPublic() throws Exception {
        assertThat(run(Mode.ENFORCE, "POST", "/api/shopify/chat/message", null).passed()).isTrue();
        assertThat(run(Mode.ENFORCE, "POST", "/api/shopify/chat/message/stream", null).passed()).isTrue();
        verify(apiKeyService, never()).validate(anyString());
    }

    @Test
    void storefrontChatWithPathParamIsPublic() throws Exception {
        assertThat(run(Mode.ENFORCE, "POST", "/api/shopify/chat/message",
            "/api/shopify/chat/message;jsessionid=abc", null).passed()).isTrue();
    }

    @Test
    void publicWorkflowRoutesArePublic() throws Exception {
        assertThat(run(Mode.ENFORCE, "POST", "/api/workflows/public/7/execute", null).passed()).isTrue();
        assertThat(run(Mode.ENFORCE, "GET", "/api/workflows/public/7", null).passed()).isTrue();
    }

    @Test
    void healthIsPublicButStatusIsNot() throws Exception {
        assertThat(run(Mode.ENFORCE, "GET", "/api/health", null).passed()).isTrue();
        assertThat(run(Mode.ENFORCE, "GET", "/api/status", null).status()).isEqualTo(401);
    }

    @Test
    void externalApiAndEmbedAreLeftToTheirOwnFilter() throws Exception {
        assertThat(run(Mode.ENFORCE, "POST", "/api/v1/agents/23/run", null).passed()).isTrue();
        assertThat(run(Mode.ENFORCE, "POST", "/api/embed/chat", null).passed()).isTrue();
    }

    @Test
    void corsPreflightPasses() throws Exception {
        assertThat(run(Mode.ENFORCE, "OPTIONS", "/api/config/chatbot", null).passed()).isTrue();
    }

    // ---- admin routes ----

    @Test
    void enforceRejectsAdminWriteWithoutKey() throws Exception {
        Outcome o = run(Mode.ENFORCE, "PUT", "/api/config/chatbot", null);
        assertThat(o.passed()).isFalse();
        assertThat(o.status()).isEqualTo(401);
    }

    @Test
    void enforceRejectsUnknownKey() throws Exception {
        Outcome o = run(Mode.ENFORCE, "PUT", "/api/config/chatbot", "hh_wrong");
        assertThat(o.passed()).isFalse();
        assertThat(o.status()).isEqualTo(401);
    }

    @Test
    void enforceGatesSensitiveReads() throws Exception {
        assertThat(run(Mode.ENFORCE, "GET", "/api/config/chatbot", null).status()).isEqualTo(401);
        assertThat(run(Mode.ENFORCE, "GET", "/api/logs/chat", null).status()).isEqualTo(401);
        assertThat(run(Mode.ENFORCE, "GET", "/api/customers", null).status()).isEqualTo(401);
        assertThat(run(Mode.ENFORCE, "GET", "/api/workflows/7", null).status()).isEqualTo(401);
        assertThat(run(Mode.ENFORCE, "POST", "/api/admin/shops/register", null).status()).isEqualTo(401);
    }

    @Test
    void adminKeyAndBootstrapKeyPassEverything() throws Exception {
        assertThat(run(Mode.ENFORCE, "PUT", "/api/config/chatbot", ADMIN_KEY).passed()).isTrue();
        assertThat(run(Mode.ENFORCE, "DELETE", "/api/agents/23", ADMIN_KEY).passed()).isTrue();
        assertThat(run(Mode.ENFORCE, "POST", "/api/agents/23/execute", ADMIN_KEY).passed()).isTrue();
        assertThat(run(Mode.ENFORCE, "PUT", "/api/config/chatbot", BOOTSTRAP).passed()).isTrue();
        verify(apiKeyService, times(3)).touchLastUsed(1L);
        verify(apiKeyService, never()).touchLastUsed(-1L); // bootstrap key has no row
    }

    @Test
    void agentsRunKeyCanOnlyExecuteAgents() throws Exception {
        // The HR app's call for agent 23
        assertThat(run(Mode.ENFORCE, "POST", "/api/agents/23/execute", HR_KEY).passed()).isTrue();

        Outcome edit = run(Mode.ENFORCE, "PUT", "/api/agents/23", HR_KEY);
        assertThat(edit.passed()).isFalse();
        assertThat(edit.status()).isEqualTo(403);
        assertThat(run(Mode.ENFORCE, "GET", "/api/agents", HR_KEY).status()).isEqualTo(403);
        assertThat(run(Mode.ENFORCE, "PUT", "/api/config/chatbot", HR_KEY).status()).isEqualTo(403);
        assertThat(run(Mode.ENFORCE, "POST", "/api/workflows/7/execute", HR_KEY).status()).isEqualTo(403);
    }

    @Test
    void workflowsRunKeyCanOnlyExecuteWorkflows() throws Exception {
        assertThat(run(Mode.ENFORCE, "POST", "/api/workflows/7/execute", WF_KEY).passed()).isTrue();
        assertThat(run(Mode.ENFORCE, "POST", "/api/agents/23/execute", WF_KEY).status()).isEqualTo(403);
    }

    @Test
    void dotDotInRawUriCannotReachAdminRoute() throws Exception {
        // Container normalises to /api/config/chatbot; raw URI starts with the public prefix.
        Outcome o = run(Mode.ENFORCE, "PUT", "/api/config/chatbot",
            "/api/shopify/chat/../../config/chatbot", null);
        assertThat(o.passed()).isFalse();
        assertThat(o.status()).isEqualTo(401);
    }

    // ---- modes ----

    @Test
    void reportModeNeverBlocks() throws Exception {
        Outcome o = run(Mode.REPORT, "PUT", "/api/config/chatbot", null);
        assertThat(o.passed()).isTrue();
        assertThat(o.status()).isEqualTo(200);
        assertThat(run(Mode.REPORT, "PUT", "/api/agents/23", HR_KEY).passed()).isTrue();
    }

    @Test
    void offModeSkipsEverything() throws Exception {
        assertThat(run(Mode.OFF, "PUT", "/api/config/chatbot", null).passed()).isTrue();
        verify(apiKeyService, never()).validate(anyString());
    }

    @Test
    void parseModeDefaultsToReportAndFailsClosedOnTypos() {
        assertThat(AdminAuthFilter.parseMode(null)).isEqualTo(Mode.REPORT);
        assertThat(AdminAuthFilter.parseMode("")).isEqualTo(Mode.REPORT);
        assertThat(AdminAuthFilter.parseMode("Enforce")).isEqualTo(Mode.ENFORCE);
        assertThat(AdminAuthFilter.parseMode(" off ")).isEqualTo(Mode.OFF);
        assertThat(AdminAuthFilter.parseMode("enforced")).isEqualTo(Mode.ENFORCE);
    }

    @Test
    void requiredScopeOnlyRelaxesExecuteRoutes() {
        assertThat(AdminAuthFilter.requiredScope("POST", "/api/agents/23/execute")).isEqualTo("agents:run");
        assertThat(AdminAuthFilter.requiredScope("POST", "/api/workflows/7/execute")).isEqualTo("workflows:run");
        assertThat(AdminAuthFilter.requiredScope("GET", "/api/agents/23/execute")).isEqualTo("admin");
        assertThat(AdminAuthFilter.requiredScope("POST", "/api/agents/23/executions")).isEqualTo("admin");
        assertThat(AdminAuthFilter.requiredScope("POST", "/api/agents/abc/execute")).isEqualTo("admin");
    }
}
