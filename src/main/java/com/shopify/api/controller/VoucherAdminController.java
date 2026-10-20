package com.shopify.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopify.api.model.voucher.VoucherLedgerEntry;
import com.shopify.api.model.voucher.VoucherOverride;
import com.shopify.api.model.voucher.VoucherSettings;
import com.shopify.api.repository.voucher.VoucherLedgerRepository;
import com.shopify.api.service.voucher.VoucherAuthGuard;
import com.shopify.api.service.voucher.VoucherClock;
import com.shopify.api.service.voucher.VoucherExclusions;
import com.shopify.api.service.voucher.VoucherOverrideService;
import com.shopify.api.service.voucher.VoucherReconcileService;
import com.shopify.api.service.voucher.VoucherReportService;
import com.shopify.api.service.voucher.VoucherScopeMonitor;
import com.shopify.api.service.voucher.VoucherSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin + owner endpoints for closing vouchers. Every handler authenticates
 * itself through {@link VoucherAuthGuard}, independent of ADMIN_AUTH_MODE.
 * Loosening any limit (or enabling) needs the owner key as well as the admin key;
 * tightening, the kill switch and revoking codes need only the admin key.
 * Nothing on the storefront chat path calls this controller or its services' write methods.
 */
@RestController
@RequestMapping("/api/vouchers")
@Slf4j
public class VoucherAdminController {

    private final VoucherAuthGuard guard;
    private final VoucherSettingsService settingsService;
    private final VoucherOverrideService overrideService;
    private final VoucherReportService reportService;
    private final VoucherReconcileService reconcileService;
    private final VoucherScopeMonitor scopeMonitor;
    private final VoucherLedgerRepository ledgerRepository;
    private final VoucherClock clock;
    private final boolean featureAllowed;

    public VoucherAdminController(VoucherAuthGuard guard, VoucherSettingsService settingsService,
                                  VoucherOverrideService overrideService, VoucherReportService reportService,
                                  VoucherReconcileService reconcileService, VoucherScopeMonitor scopeMonitor,
                                  VoucherLedgerRepository ledgerRepository, VoucherClock clock,
                                  @Value("${vouchers.feature-allowed:true}") boolean featureAllowed) {
        this.guard = guard;
        this.settingsService = settingsService;
        this.overrideService = overrideService;
        this.reportService = reportService;
        this.reconcileService = reconcileService;
        this.scopeMonitor = scopeMonitor;
        this.ledgerRepository = ledgerRepository;
        this.clock = clock;
        this.featureAllowed = featureAllowed;
    }

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSettings(HttpServletRequest request) {
        VoucherAuthGuard.Result auth = guard.checkAdmin(request);
        if (!auth.ok()) return deny(auth);
        return ResponseEntity.ok(settingsView(settingsService.current()));
    }

    /** Partial update. Fields that loosen a limit need X-Owner-Key too. */
    @PutMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody JsonNode body, HttpServletRequest request) {
        VoucherAuthGuard.Result auth = guard.checkAdmin(request);
        if (!auth.ok()) return deny(auth);
        VoucherSettings prev = settingsService.current();
        VoucherSettings next;
        try {
            next = merge(prev, body);
            VoucherSettingsService.validate(next);
        } catch (IllegalArgumentException e) {
            return error(400, e.getMessage());
        }
        List<String> loosened = VoucherSettingsService.loosenings(prev, next);
        String by = auth.identity();
        if (!loosened.isEmpty()) {
            VoucherAuthGuard.Result owner = guard.checkOwner(request);
            if (!owner.ok()) {
                Map<String, Object> err = errorBody(owner.status() == 403 || owner.status() == 401
                    ? "These changes loosen limits and need the owner key: " + String.join(", ", loosened)
                    : owner.error());
                err.put("needs_owner_key", loosened);
                return ResponseEntity.status(owner.status()).body(err);
            }
            by = by + " +owner";
        }
        next.setUpdatedBy(by);
        next.setUpdatedAt(clock.now());
        VoucherSettings saved = settingsService.save(next);
        log.warn("VOUCHER_SETTINGS updated by='{}' loosened={} enabled={} pool={} maxPct={} maxAmount={} min={} validity={} holdout={}",
            by, loosened, saved.isEnabled(), saved.getDailyPool(), saved.getMaxPct(), saved.getMaxAmount(),
            saved.getMinSubtotal(), saved.getValidityMinutes(), saved.getHoldoutPct());
        if (saved.isEnabled()) scopeMonitor.refresh();
        return ResponseEntity.ok(settingsView(saved));
    }

    /** Kill switch: admin key only. Live codes stay valid until they expire (or revoke them). */
    @PostMapping("/kill")
    public ResponseEntity<Map<String, Object>> kill(HttpServletRequest request) {
        VoucherAuthGuard.Result auth = guard.checkAdmin(request);
        if (!auth.ok()) return deny(auth);
        VoucherSettings saved = settingsService.disable(auth.identity());
        log.warn("VOUCHER_KILL_SWITCH by='{}'", auth.identity());
        return ResponseEntity.ok(settingsView(saved));
    }

    @GetMapping("/ledger")
    public ResponseEntity<Map<String, Object>> ledger(@RequestParam(required = false) String date,
                                                      HttpServletRequest request) {
        VoucherAuthGuard.Result auth = guard.checkAdmin(request);
        if (!auth.ok()) return deny(auth);
        LocalDate day;
        try {
            day = date == null ? clock.today() : LocalDate.parse(date);
        } catch (Exception e) {
            return error(400, "date must be YYYY-MM-DD");
        }
        List<Map<String, Object>> rows = ledgerRepository.findByPoolDateOrderByIssuedAtDesc(day).stream()
            .map(VoucherAdminController::ledgerView).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", day.toString());
        body.put("entries", rows);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> report(@RequestParam(required = false) String date,
                                                      @RequestParam(defaultValue = "false") boolean store,
                                                      HttpServletRequest request) {
        VoucherAuthGuard.Result auth = guard.checkAdmin(request);
        if (!auth.ok()) return deny(auth);
        try {
            LocalDate day = date == null ? clock.today() : LocalDate.parse(date);
            return ResponseEntity.ok(reportService.report(day, store));
        } catch (java.time.format.DateTimeParseException e) {
            return error(400, "date must be YYYY-MM-DD");
        }
    }

    /** Cancel a live code now and return its value: admin key only (it only tightens). */
    @PostMapping("/ledger/{id}/revoke")
    public ResponseEntity<Map<String, Object>> revokeCode(@PathVariable Long id, HttpServletRequest request) {
        VoucherAuthGuard.Result auth = guard.checkAdmin(request);
        if (!auth.ok()) return deny(auth);
        VoucherLedgerEntry entry = ledgerRepository.findById(id).orElse(null);
        if (entry == null) return error(404, "No such voucher");
        boolean done;
        try {
            done = reconcileService.revoke(entry);
        } catch (Exception e) {
            return error(502, "Shopify call failed; try again");
        }
        if (!done) return error(409, "Voucher is not live (already used, settled, or Shopify delete failed)");
        log.warn("VOUCHER_REVOKED id={} by='{}'", id, auth.identity());
        return ResponseEntity.ok(ledgerView(ledgerRepository.findById(id).orElse(entry)));
    }

    @PostMapping("/reconcile")
    public ResponseEntity<Map<String, Object>> reconcileNow(HttpServletRequest request) {
        VoucherAuthGuard.Result auth = guard.checkAdmin(request);
        if (!auth.ok()) return deny(auth);
        VoucherReconcileService.Summary s = reconcileService.reconcile();
        return ResponseEntity.ok(Map.of("redeemed", s.redeemed(), "expired", s.expired(),
            "failed", s.failed(), "deferred", s.deferred()));
    }

    @PostMapping("/scope-check")
    public ResponseEntity<Map<String, Object>> scopeCheck(HttpServletRequest request) {
        VoucherAuthGuard.Result auth = guard.checkAdmin(request);
        if (!auth.ok()) return deny(auth);
        scopeMonitor.refresh();
        Map<String, Object> body = new HashMap<>();
        body.put("write_discounts", scopeMonitor.state());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/overrides")
    public ResponseEntity<Map<String, Object>> overrides(HttpServletRequest request) {
        VoucherAuthGuard.Result auth = guard.checkAdmin(request);
        if (!auth.ok()) return deny(auth);
        return ResponseEntity.ok(Map.of("overrides", overrideService.recent().stream()
            .map(this::overrideView).toList()));
    }

    /**
     * Owner command: needs X-API-Key (admin) AND X-Owner-Key, in every auth mode.
     * Body: {"type":"POOL_RAISE|SESSION_AMOUNT|PAUSE","value":20,"target":"<session id>",
     *        "durationMinutes":120,"who":"Nic","reason":"..."}
     */
    @PostMapping("/override")
    public ResponseEntity<Map<String, Object>> createOverride(@RequestBody JsonNode body, HttpServletRequest request) {
        VoucherAuthGuard.Result auth = guard.checkOwner(request);
        if (!auth.ok()) return deny(auth);
        try {
            VoucherOverride.Type type = VoucherOverride.Type.valueOf(text(body, "type", "").toUpperCase());
            BigDecimal value = body.hasNonNull("value") ? new BigDecimal(body.get("value").asText()) : null;
            Integer minutes = body.hasNonNull("durationMinutes") ? body.get("durationMinutes").asInt() : null;
            String who = text(body, "who", "").trim();
            if (who.isEmpty() || who.length() > 100) return error(400, "who is required (max 100 chars)");
            VoucherOverride saved = overrideService.create(
                new VoucherOverrideService.Request(type, text(body, "target", null), value, minutes, text(body, "reason", null)),
                who + " (key:" + auth.identity() + ")", VoucherAuthGuard.clientIp(request));
            return ResponseEntity.ok(overrideView(saved));
        } catch (IllegalArgumentException e) {
            return error(400, e.getMessage() == null || e.getMessage().startsWith("No enum")
                ? "type must be POOL_RAISE, SESSION_AMOUNT or PAUSE" : e.getMessage());
        }
    }

    @PostMapping("/override/{id}/revoke")
    public ResponseEntity<Map<String, Object>> revokeOverride(@PathVariable Long id, HttpServletRequest request) {
        VoucherAuthGuard.Result auth = guard.checkOwner(request);
        if (!auth.ok()) return deny(auth);
        return overrideService.revoke(id, auth.identity())
            .map(o -> ResponseEntity.ok(overrideView(o)))
            .orElseGet(() -> error(404, "No such override"));
    }

    // ---------------------------------------------------------------------

    private static VoucherSettings merge(VoucherSettings prev, JsonNode b) {
        if (b == null || !b.isObject()) throw new IllegalArgumentException("JSON object body required");
        VoucherSettings.VoucherSettingsBuilder n = prev.toBuilder();
        if (b.has("enabled")) n.enabled(bool(b, "enabled"));
        if (b.has("dailyPool")) n.dailyPool(decimal(b, "dailyPool"));
        if (b.has("maxPct")) n.maxPct(decimal(b, "maxPct"));
        if (b.has("maxAmount")) n.maxAmount(decimal(b, "maxAmount"));
        if (b.has("minSubtotal")) n.minSubtotal(decimal(b, "minSubtotal"));
        if (b.has("validityMinutes")) n.validityMinutes(integer(b, "validityMinutes"));
        if (b.has("holdoutPct")) n.holdoutPct(integer(b, "holdoutPct"));
        if (b.has("maxPerIpPerDay")) n.maxPerIpPerDay(integer(b, "maxPerIpPerDay"));
        if (b.has("exclusions")) n.exclusions(VoucherExclusions.fromNode(b.get("exclusions")).toJson());
        return n.build();
    }

    private static boolean bool(JsonNode b, String f) {
        if (!b.get(f).isBoolean()) throw new IllegalArgumentException(f + " must be true or false");
        return b.get(f).asBoolean();
    }

    private static BigDecimal decimal(JsonNode b, String f) {
        try {
            return new BigDecimal(b.get(f).asText());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(f + " must be a number");
        }
    }

    private static int integer(JsonNode b, String f) {
        if (!b.get(f).canConvertToInt()) throw new IllegalArgumentException(f + " must be a whole number");
        return b.get(f).asInt();
    }

    private static String text(JsonNode b, String f, String dflt) {
        return b != null && b.hasNonNull(f) ? b.get(f).asText() : dflt;
    }

    private Map<String, Object> settingsView(VoucherSettings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", s.isEnabled());
        m.put("dailyPool", s.getDailyPool());
        m.put("maxPct", s.getMaxPct());
        m.put("maxAmount", s.getMaxAmount());
        m.put("minSubtotal", s.getMinSubtotal());
        m.put("validityMinutes", s.getValidityMinutes());
        m.put("holdoutPct", s.getHoldoutPct());
        m.put("maxPerIpPerDay", s.getMaxPerIpPerDay());
        VoucherExclusions ex = VoucherExclusions.parse(s.getExclusions());
        m.put("exclusions", Map.of("vendors", ex.vendors(), "productTypes", ex.productTypes(), "tags", ex.tags()));
        m.put("updatedAt", s.getUpdatedAt() == null ? null : s.getUpdatedAt().toString());
        m.put("updatedBy", s.getUpdatedBy());
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("featureAllowed", featureAllowed);
        status.put("writeDiscountsScope", scopeMonitor.state());
        status.put("ownerKeyConfigured", guard.ownerKeyConfigured());
        status.put("paused", overrideService.paused());
        status.put("poolRaiseToday", overrideService.poolRaise(clock.today()));
        m.put("status", status);
        return m;
    }

    private static Map<String, Object> ledgerView(VoucherLedgerEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("code", e.getCode());
        m.put("amount", e.getAmount());
        m.put("status", e.getStatus().name());
        m.put("sessionId", e.getSessionId());
        m.put("knownCustomer", e.getCustomerKey() != null);
        m.put("cartSubtotal", e.getCartSubtotal());
        m.put("minSubtotal", e.getMinSubtotal());
        m.put("reason", e.getReason());
        m.put("issuedAt", e.getIssuedAt().toString());
        m.put("expiresAt", e.getExpiresAt().toString());
        m.put("resolvedAt", e.getResolvedAt() == null ? null : e.getResolvedAt().toString());
        m.put("orderId", e.getOrderId());
        m.put("orderTotal", e.getOrderTotal());
        m.put("lastError", e.getLastError());
        return m;
    }

    private Map<String, Object> overrideView(VoucherOverride o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("type", o.getType().name());
        m.put("target", o.getTarget());
        m.put("value", o.getValue());
        m.put("poolDate", o.getPoolDate() == null ? null : o.getPoolDate().toString());
        m.put("reason", o.getReason());
        m.put("createdBy", o.getCreatedBy());
        m.put("createdAt", o.getCreatedAt().toString());
        m.put("expiresAt", o.getExpiresAt().toString());
        m.put("revokedAt", o.getRevokedAt() == null ? null : o.getRevokedAt().toString());
        m.put("active", o.isActive(clock.now()));
        return m;
    }

    private static ResponseEntity<Map<String, Object>> deny(VoucherAuthGuard.Result auth) {
        return ResponseEntity.status(auth.status()).body(errorBody(auth.error()));
    }

    private static ResponseEntity<Map<String, Object>> error(int status, String message) {
        return ResponseEntity.status(status).body(errorBody(message));
    }

    private static Map<String, Object> errorBody(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("error", message);
        return m;
    }
}
