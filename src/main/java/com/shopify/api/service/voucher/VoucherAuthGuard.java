package com.shopify.api.service.voucher;

import com.shopify.api.model.ApiKey;
import com.shopify.api.service.ApiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Auth for /api/vouchers/**, checked in the controller itself so it holds in every
 * ADMIN_AUTH_MODE (off, report, enforce):
 *  - admin: a valid X-API-Key with the admin scope (api_keys row or ADMIN_BOOTSTRAP_KEY);
 *  - owner: admin AND X-Owner-Key equal to VOUCHER_OWNER_KEY (constant-time compare).
 * The owner key is disabled unless configured with at least 32 characters. Repeated
 * wrong owner keys lock the owner endpoints for 15 minutes.
 */
@Component
@Slf4j
public class VoucherAuthGuard {

    public static final String ADMIN_HEADER = "X-API-Key";
    public static final String OWNER_HEADER = "X-Owner-Key";
    static final int MIN_OWNER_KEY_LENGTH = 32;
    static final int MAX_FAILURES = 5;
    static final long WINDOW_MS = 15 * 60_000L;

    public record Result(int status, String error, String identity) {
        public boolean ok() {
            return status == 0;
        }
    }

    private final ApiKeyService apiKeyService;
    private final String bootstrapKey;
    private final String ownerKey;
    private final Deque<Long> failures = new ArrayDeque<>();
    private volatile long lockedUntil;

    public VoucherAuthGuard(ApiKeyService apiKeyService,
                            @Value("${admin.bootstrap-key:#{null}}") String bootstrapKey,
                            @Value("${vouchers.owner-key:}") String ownerKey) {
        this.apiKeyService = apiKeyService;
        this.bootstrapKey = bootstrapKey;
        this.ownerKey = ownerKey == null ? "" : ownerKey.trim();
        if (!this.ownerKey.isEmpty() && this.ownerKey.length() < MIN_OWNER_KEY_LENGTH) {
            log.error("voucher: VOUCHER_OWNER_KEY is shorter than {} chars; owner overrides are DISABLED",
                MIN_OWNER_KEY_LENGTH);
        }
    }

    public boolean ownerKeyConfigured() {
        return ownerKey.length() >= MIN_OWNER_KEY_LENGTH;
    }

    public Result checkAdmin(HttpServletRequest request) {
        Optional<String> identity = adminIdentity(request.getHeader(ADMIN_HEADER));
        if (identity.isEmpty()) {
            log.warn("voucher-auth rejected {} {} (missing/invalid admin key) ip={}",
                request.getMethod(), request.getRequestURI(), clientIp(request));
            return new Result(401, "Valid admin X-API-Key required", null);
        }
        return new Result(0, null, identity.get());
    }

    public Result checkOwner(HttpServletRequest request) {
        Result admin = checkAdmin(request);
        if (!admin.ok()) return admin;
        if (!ownerKeyConfigured()) return new Result(503, "Owner key is not configured", null);
        if (isLocked()) {
            log.warn("voucher-auth owner endpoints locked; rejected {} {} ip={}",
                request.getMethod(), request.getRequestURI(), clientIp(request));
            return new Result(429, "Too many failed owner-key attempts; try again later", null);
        }
        String presented = request.getHeader(OWNER_HEADER);
        if (presented == null || !constantTimeEquals(ownerKey, presented.trim())) {
            recordFailure();
            log.warn("voucher-auth rejected {} {} (bad owner key) by='{}' ip={}",
                request.getMethod(), request.getRequestURI(), admin.identity(), clientIp(request));
            return new Result(403, "Valid X-Owner-Key required", null);
        }
        return new Result(0, null, admin.identity());
    }

    private Optional<String> adminIdentity(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return Optional.empty();
        if (bootstrapKey != null && !bootstrapKey.isBlank() && constantTimeEquals(bootstrapKey, rawKey)) {
            return Optional.of("bootstrap");
        }
        try {
            return apiKeyService.validate(rawKey).filter(k -> k.hasScope("admin")).map(ApiKey::getName);
        } catch (Exception e) {
            log.warn("voucher-auth: key lookup failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private synchronized boolean isLocked() {
        return System.currentTimeMillis() < lockedUntil;
    }

    private synchronized void recordFailure() {
        long now = System.currentTimeMillis();
        failures.addLast(now);
        while (!failures.isEmpty() && now - failures.peekFirst() > WINDOW_MS) failures.removeFirst();
        if (failures.size() >= MAX_FAILURES) {
            lockedUntil = now + WINDOW_MS;
            failures.clear();
            log.error("voucher-auth: {} wrong owner keys in 15 min; owner endpoints locked for 15 min", MAX_FAILURES);
        }
    }

    /** Hash both sides first so the comparison time doesn't depend on length either. */
    static boolean constantTimeEquals(String expected, String presented) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] a = md.digest(expected.getBytes(StandardCharsets.UTF_8));
            byte[] b = MessageDigest.getInstance("SHA-256").digest(presented.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(a, b);
        } catch (Exception e) {
            return false;
        }
    }

    public static String clientIp(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        return fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : request.getRemoteAddr();
    }
}
