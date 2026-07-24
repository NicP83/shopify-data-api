package com.shopify.api.service;

import com.shopify.api.model.ApiKey;
import com.shopify.api.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues and validates API keys, and enforces a simple per-key inbound
 * rate limit (fixed window, one minute).
 *
 * Raw keys are never stored — only their SHA-256 hash. A raw key is returned
 * exactly once, from {@link #createKey}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {

    private static final String KEY_PREFIX = "hh_";
    private static final int RAW_KEY_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;

    // key id -> sliding one-minute counter
    private final ConcurrentHashMap<Long, Window> rateWindows = new ConcurrentHashMap<>();

    /** Result of creating a key: the entity plus the one-time raw key. */
    public record CreatedKey(ApiKey apiKey, String rawKey) {}

    @Transactional
    public CreatedKey createKey(String name, String scopes, Integer rateLimitPerMin,
                                String refererAllowlist, Long boundAgentId, Long boundWorkflowId) {
        byte[] raw = new byte[RAW_KEY_BYTES];
        RANDOM.nextBytes(raw);
        String rawKey = KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        ApiKey entity = ApiKey.builder()
            .name(name)
            .keyPrefix(rawKey.substring(0, Math.min(12, rawKey.length())))
            .keyHash(hash(rawKey))
            .scopes(scopes == null ? "" : scopes)
            .rateLimitPerMin(rateLimitPerMin != null ? rateLimitPerMin : 60)
            .active(true)
            .refererAllowlist(refererAllowlist)
            .boundAgentId(boundAgentId)
            .boundWorkflowId(boundWorkflowId)
            .createdAt(LocalDateTime.now())
            .build();

        ApiKey saved = apiKeyRepository.save(entity);
        log.info("Created API key '{}' (id {}) with scopes [{}]", name, saved.getId(), scopes);
        return new CreatedKey(saved, rawKey);
    }

    /** Validate a raw key; returns the active ApiKey if the hash matches. */
    @Transactional(readOnly = true)
    public Optional<ApiKey> validate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        return apiKeyRepository.findByKeyHash(hash(rawKey))
            .filter(k -> Boolean.TRUE.equals(k.getActive()));
    }

    /**
     * Record one request against the key's per-minute budget.
     * @return true if the request is within budget, false if it should be throttled.
     */
    public boolean allowRequest(ApiKey key) {
        int limit = key.getRateLimitPerMin() != null ? key.getRateLimitPerMin() : 60;
        long minute = System.currentTimeMillis() / 60_000L;
        Window w = rateWindows.compute(key.getId(), (id, existing) -> {
            if (existing == null || existing.minute != minute) {
                return new Window(minute, 1);
            }
            existing.count++;
            return existing;
        });
        return w.count <= limit;
    }

    @Transactional
    public void touchLastUsed(Long keyId) {
        apiKeyRepository.findById(keyId).ifPresent(k -> {
            k.setLastUsedAt(LocalDateTime.now());
            apiKeyRepository.save(k);
        });
    }

    private String hash(String rawKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static final class Window {
        final long minute;
        int count;
        Window(long minute, int count) {
            this.minute = minute;
            this.count = count;
        }
    }
}
