package com.shopify.api.controller.v1;

import com.shopify.api.filter.ApiKeyAuthFilter;
import com.shopify.api.model.ApiKey;
import com.shopify.api.repository.ApiKeyRepository;
import com.shopify.api.service.ApiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manage API keys. Requires an admin-scoped key (or the bootstrap key).
 * Lives under /api/v1 so it is covered by ApiKeyAuthFilter.
 */
@RestController
@RequestMapping("/api/v1/admin/keys")
@RequiredArgsConstructor
@Slf4j
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final ApiKeyRepository apiKeyRepository;

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request) {
        ResponseEntity<?> denied = requireAdmin(request);
        if (denied != null) return denied;
        List<Map<String, Object>> keys = apiKeyRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toSummary)
            .collect(Collectors.toList());
        return ResponseEntity.ok(keys);
    }

    @PostMapping
    public ResponseEntity<?> create(HttpServletRequest request, @RequestBody CreateKeyRequest body) {
        ResponseEntity<?> denied = requireAdmin(request);
        if (denied != null) return denied;

        if (body.getName() == null || body.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }

        ApiKeyService.CreatedKey created = apiKeyService.createKey(
            body.getName(),
            body.getScopes(),
            body.getRateLimitPerMin(),
            body.getRefererAllowlist(),
            body.getBoundAgentId(),
            body.getBoundWorkflowId());

        Map<String, Object> resp = toSummary(created.apiKey());
        // The raw key is returned exactly once, here.
        resp.put("rawKey", created.rawKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> revoke(HttpServletRequest request, @PathVariable Long id) {
        ResponseEntity<?> denied = requireAdmin(request);
        if (denied != null) return denied;
        return apiKeyRepository.findById(id).map(k -> {
            k.setActive(false);
            apiKeyRepository.save(k);
            return ResponseEntity.ok(Map.of("message", "revoked", "id", id));
        }).orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<?> requireAdmin(HttpServletRequest request) {
        Object attr = request.getAttribute(ApiKeyAuthFilter.ATTR_API_KEY);
        if (!(attr instanceof ApiKey key) || !key.hasScope("admin")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "admin scope required"));
        }
        return null;
    }

    private Map<String, Object> toSummary(ApiKey k) {
        java.util.HashMap<String, Object> m = new java.util.HashMap<>();
        m.put("id", k.getId());
        m.put("name", k.getName());
        m.put("keyPrefix", k.getKeyPrefix());
        m.put("scopes", k.getScopeList());
        m.put("rateLimitPerMin", k.getRateLimitPerMin());
        m.put("active", k.getActive());
        m.put("refererAllowlist", k.getRefererAllowlist());
        m.put("boundAgentId", k.getBoundAgentId());
        m.put("boundWorkflowId", k.getBoundWorkflowId());
        m.put("createdAt", k.getCreatedAt());
        m.put("lastUsedAt", k.getLastUsedAt());
        return m;
    }

    @Data
    public static class CreateKeyRequest {
        private String name;
        private String scopes;
        private Integer rateLimitPerMin;
        private String refererAllowlist;
        private Long boundAgentId;
        private Long boundWorkflowId;
    }
}
