package com.shopify.api.filter;

import com.shopify.api.model.ApiKey;
import com.shopify.api.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Requires an X-API-Key on the admin API: every /api/** route except an explicit
 * public allowlist (storefront chat, public workflows, health, and the
 * /api/v1 + /api/embed surfaces, which {@link ApiKeyAuthFilter} already guards).
 *
 * Keys are the same ones {@link ApiKeyAuthFilter} accepts (api_keys table, or the
 * ADMIN_BOOTSTRAP_KEY). Scope needed:
 *   POST /api/agents/{id}/execute     -> agents:run or admin  (HR app, agent 23)
 *   POST /api/workflows/{id}/execute  -> workflows:run or admin
 *   everything else                   -> admin
 * A non-admin key with bound_agent_id / bound_workflow_id may execute only that
 * target (403 otherwise); see {@link ApiKey#mayRunAgent}.
 *
 * Mode (admin.auth.mode / ADMIN_AUTH_MODE):
 *   off     - filter does nothing
 *   report  - (default) never blocks; logs a WARN for each request that enforce would reject
 *   enforce - rejects with 401 (missing/invalid key) or 403 (missing scope)
 */
@Slf4j
public class AdminAuthFilter extends OncePerRequestFilter {

    public enum Mode { OFF, REPORT, ENFORCE }

    private static final String HEADER = "X-API-Key";

    private static final List<String> PUBLIC_PREFIXES = List.of(
        "/api/shopify/chat/",     // storefront chatbot (theme extension)
        "/api/workflows/public/", // public workflow forms
        "/api/v1/",               // guarded by ApiKeyAuthFilter
        "/api/embed/"             // guarded by ApiKeyAuthFilter
    );
    private static final List<String> PUBLIC_EXACT = List.of("/api/health");

    private static final Pattern AGENT_EXECUTE = Pattern.compile("^/api/agents/(\\d+)/execute/?$");
    private static final Pattern WORKFLOW_EXECUTE = Pattern.compile("^/api/workflows/(\\d+)/execute/?$");
    private static final Pattern PATH_PARAMS = Pattern.compile(";[^/]*");

    private final ApiKeyService apiKeyService;
    private final String bootstrapKey;
    private final Mode mode;

    public AdminAuthFilter(ApiKeyService apiKeyService, String bootstrapKey, Mode mode) {
        this.apiKeyService = apiKeyService;
        this.bootstrapKey = bootstrapKey;
        this.mode = mode;
    }

    public static Mode parseMode(String raw) {
        if (raw == null || raw.isBlank()) return Mode.REPORT;
        try {
            return Mode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // Unknown value: fail closed rather than silently leaving the API open
            log.error("Unknown admin.auth.mode '{}', falling back to ENFORCE", raw);
            return Mode.ENFORCE;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (mode == Mode.OFF) return true;
        // CORS preflight carries no credentials
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        // Public only if both the container-normalised path and the raw URI agree,
        // so ".." or ";param" tricks can't smuggle an admin route past the allowlist.
        return isPublic(normalisedPath(request)) && isPublic(rawPath(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = normalisedPath(request);
        String required = requiredScope(request.getMethod(), path);
        Optional<ApiKey> key = resolve(request.getHeader(HEADER));

        int status = 0;
        String error = null;
        if (key.isEmpty()) {
            status = HttpServletResponse.SC_UNAUTHORIZED;
            error = "Missing or invalid API key";
        } else if (!key.get().hasScope("admin") && !key.get().hasScope(required)) {
            status = HttpServletResponse.SC_FORBIDDEN;
            error = "scope '" + required + "' required";
        } else if (!bindingAllows(key.get(), path)) {
            status = HttpServletResponse.SC_FORBIDDEN;
            error = "API key is bound to a different agent or workflow";
        }

        if (status == 0) {
            if (key.get().getId() != null && key.get().getId() > 0) {
                apiKeyService.touchLastUsed(key.get().getId());
            }
            request.setAttribute(ApiKeyAuthFilter.ATTR_API_KEY, key.get());
            chain.doFilter(request, response);
            return;
        }

        if (mode == Mode.REPORT) {
            log.warn("admin-auth REPORT would reject {} {} -> {} ({}); ua='{}' ip={}",
                request.getMethod(), path, status, error,
                request.getHeader("User-Agent"), clientIp(request));
            chain.doFilter(request, response);
            return;
        }

        log.warn("admin-auth rejected {} {} -> {} ({}); ua='{}' ip={}",
            request.getMethod(), path, status, error,
            request.getHeader("User-Agent"), clientIp(request));
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"error\":\"" + error + "\"}");
    }

    static boolean isPublic(String path) {
        if (PUBLIC_EXACT.contains(path)) return true;
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    static String requiredScope(String method, String path) {
        if ("POST".equalsIgnoreCase(method)) {
            if (AGENT_EXECUTE.matcher(path).matches()) return "agents:run";
            if (WORKFLOW_EXECUTE.matcher(path).matches()) return "workflows:run";
        }
        return "admin";
    }

    /** Only the execute routes are reachable by non-admin keys, so only they need a binding check. */
    static boolean bindingAllows(ApiKey key, String path) {
        Matcher agent = AGENT_EXECUTE.matcher(path);
        if (agent.matches()) return key.mayRunAgent(parseId(agent.group(1)));
        Matcher workflow = WORKFLOW_EXECUTE.matcher(path);
        if (workflow.matches()) return key.mayRunWorkflow(parseId(workflow.group(1)));
        return true;
    }

    private static Long parseId(String digits) {
        try {
            return Long.valueOf(digits);
        } catch (NumberFormatException e) {
            return null; // overflow: matches no binding
        }
    }

    private Optional<ApiKey> resolve(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return Optional.empty();
        if (bootstrapKey != null && !bootstrapKey.isBlank() && constantTimeEquals(bootstrapKey, rawKey)) {
            return Optional.of(ApiKey.builder().id(-1L).name("bootstrap").scopes("admin").active(true).build());
        }
        return apiKeyService.validate(rawKey);
    }

    private static String normalisedPath(HttpServletRequest request) {
        String servletPath = request.getServletPath() == null ? "" : request.getServletPath();
        String pathInfo = request.getPathInfo() == null ? "" : request.getPathInfo();
        return servletPath + pathInfo;
    }

    private static String rawPath(HttpServletRequest request) {
        String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
        String ctx = request.getContextPath() == null ? "" : request.getContextPath();
        if (!ctx.isEmpty() && uri.startsWith(ctx)) uri = uri.substring(ctx.length());
        return PATH_PARAMS.matcher(uri).replaceAll("");
    }

    private static String clientIp(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        return fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : request.getRemoteAddr();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
