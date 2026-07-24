package com.shopify.api.filter;

import com.shopify.api.model.ApiKey;
import com.shopify.api.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Authenticates requests to the new authenticated surface (/api/v1/**) with an
 * X-API-Key header. Validates the key, enforces a per-key rate limit, and
 * exposes the resolved {@link ApiKey} as the request attribute
 * {@code apiKey} for downstream controllers to check scopes.
 *
 * This filter is scoped to /api/v1/** only (see ApiKeyFilterConfig) so the
 * storefront chat endpoint and the existing admin routes are unaffected.
 *
 * A bootstrap key (env ADMIN_BOOTSTRAP_KEY) is accepted as a synthetic
 * admin-scoped key so the owner can mint real keys before any exist.
 */
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_API_KEY = "apiKey";
    private static final String HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;
    private final String bootstrapKey;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService,
                            @Value("${admin.bootstrap-key:#{null}}") String bootstrapKey) {
        this.apiKeyService = apiKeyService;
        this.bootstrapKey = bootstrapKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Let CORS preflight through untouched
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String rawKey = request.getHeader(HEADER);

        // Bootstrap key: synthetic admin key, only if configured and non-empty.
        // Constant-time compare so a timing side channel can't reveal the key.
        if (bootstrapKey != null && !bootstrapKey.isBlank() && constantTimeEquals(bootstrapKey, rawKey)) {
            ApiKey synthetic = ApiKey.builder()
                .id(-1L)
                .name("bootstrap")
                .scopes("admin,agents:run,workflows:run")
                .rateLimitPerMin(1000)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
            request.setAttribute(ATTR_API_KEY, synthetic);
            chain.doFilter(request, response);
            return;
        }

        Optional<ApiKey> keyOpt = apiKeyService.validate(rawKey);
        if (keyOpt.isEmpty()) {
            unauthorized(response, "Missing or invalid API key");
            return;
        }

        ApiKey key = keyOpt.get();
        if (!apiKeyService.allowRequest(key)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
            return;
        }

        apiKeyService.touchLastUsed(key.getId());
        request.setAttribute(ATTR_API_KEY, key);
        chain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String a, String b) {
        byte[] ab = a == null ? new byte[0] : a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bb = b == null ? new byte[0] : b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(ab, bb);
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
