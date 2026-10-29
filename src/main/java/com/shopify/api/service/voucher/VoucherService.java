package com.shopify.api.service.voucher;

import com.shopify.api.model.voucher.VoucherLedgerEntry;
import com.shopify.api.model.voucher.VoucherOffer;
import com.shopify.api.model.voucher.VoucherSettings;
import com.shopify.api.model.voucher.VoucherStatus;
import com.shopify.api.service.voucher.VoucherShopifyGateway.CodeSpec;
import com.shopify.api.service.voucher.VoucherShopifyGateway.CodeState;
import com.shopify.api.service.voucher.VoucherShopifyGateway.CreateResult;
import com.shopify.api.service.voucher.VoucherShopifyGateway.GatewayException;
import com.shopify.api.service.voucher.VoucherShopifyGateway.VariantInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Issues closing vouchers. The model chooses when and how much; this class
 * enforces every limit from server-side state only: the cart subtotal is
 * recomputed from Shopify prices, and caps come from voucher_settings, the
 * daily pool and owner overrides. Nothing in the tool input can raise a limit.
 */
@Service
@Slf4j
public class VoucherService {

    public static final String VARIANT_GID_PREFIX = "gid://shopify/ProductVariant/";
    private static final Pattern VARIANT_ID = Pattern.compile("^(?:gid://shopify/ProductVariant/)?(\\d{1,20})$");
    /** Deliberately strict (no quotes, spaces or backslashes): the value goes into a Shopify search query. */
    private static final Pattern EMAIL = Pattern.compile("^[a-z0-9._%+'-]{1,64}@[a-z0-9-]{1,63}(\\.[a-z0-9-]{1,63})*\\.[a-z]{2,24}$");
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("h:mm a z", Locale.ENGLISH);

    /** Raw tool input; every field is untrusted. */
    public record RawLine(String variantId, BigDecimal quantity) {}

    public record OfferInput(List<RawLine> lines, BigDecimal requestedAmount, String reason, String customerEmail) {}

    /** status: "issued" or "refused"; fields are what the tool returns to the model. */
    public record OfferResult(String status, Map<String, Object> fields) {
        public boolean issued() {
            return "issued".equals(status);
        }
    }

    private record Line(String variantGid, String numericId, int quantity) {}

    private final VoucherSettingsService settingsService;
    private final VoucherOverrideService overrideService;
    private final VoucherStore store;
    private final VoucherShopifyGateway gateway;
    private final VoucherScopeMonitor scopeMonitor;
    private final VoucherClock clock;
    private final String shopUrl;
    private final boolean featureAllowed;

    private volatile boolean pausedCached;
    private volatile long pausedCheckedAt;
    private final java.util.concurrent.atomic.AtomicBoolean pauseRefreshing = new java.util.concurrent.atomic.AtomicBoolean();

    public VoucherService(VoucherSettingsService settingsService, VoucherOverrideService overrideService,
                          VoucherStore store, VoucherShopifyGateway gateway, VoucherScopeMonitor scopeMonitor,
                          VoucherClock clock,
                          @Value("${shopify.shop-url}") String shopUrl,
                          @Value("${vouchers.feature-allowed:true}") boolean featureAllowed) {
        this.settingsService = settingsService;
        this.overrideService = overrideService;
        this.store = store;
        this.gateway = gateway;
        this.scopeMonitor = scopeMonitor;
        this.clock = clock;
        this.shopUrl = shopUrl;
        this.featureAllowed = featureAllowed;
    }

    /**
     * Whether to offer the tool this turn (cached up to 15s). Off unless the feature
     * flag, the DB switch and a confirmed write_discounts scope all agree, and no
     * owner PAUSE is active.
     */
    public boolean toolAvailable() {
        if (!featureAllowed) return false;
        VoucherSettings settings = settingsService.cached();
        if (!settings.isEnabled()) return false;
        if (!scopeMonitor.ready()) return false;
        return !pausedCachedValue();
    }

    /** e.g. "$150" (cached settings). */
    public String minimumSpendLabel() {
        return VoucherRules.dollars(settingsService.cached().getMinSubtotal());
    }

    /**
     * Amount hint for the prompt guidance, built from the tier settings, e.g.
     * "$5 or $10 for carts under $500; for carts of $500 or more, $15 to $25". Advisory only:
     * the tool still recomputes the cart and applies every cap.
     */
    public String amountGuidanceLabel() {
        VoucherSettings s = settingsService.cached();
        String small = VoucherRules.dollars(s.getMaxAmount());
        String threshold = VoucherRules.dollars(s.getHighTierSubtotal());
        String high = VoucherRules.dollars(s.getHighTierMaxAmount());
        String smallRange = s.getMaxAmount().compareTo(new java.math.BigDecimal("10")) >= 0 ? "$5 or " + small : small;
        if (s.getHighTierMaxAmount().compareTo(s.getMaxAmount()) <= 0) {
            return smallRange;
        }
        java.math.BigDecimal highLow = s.getMaxAmount().add(new java.math.BigDecimal("5")).min(s.getHighTierMaxAmount());
        String highRange = highLow.compareTo(s.getHighTierMaxAmount()) < 0
            ? VoucherRules.dollars(highLow) + " to " + high : high;
        return smallRange + " for carts under " + threshold + "; for carts of " + threshold + " or more, " + highRange;
    }

    /** Cached like the settings: refreshed off-thread, never a DB call on the chat turn's thread. */
    private boolean pausedCachedValue() {
        if (System.currentTimeMillis() - pausedCheckedAt > 15_000 && pauseRefreshing.compareAndSet(false, true)) {
            Schedulers.boundedElastic().schedule(this::refreshPaused);
        }
        return pausedCached;
    }

    private void refreshPaused() {
        try {
            pausedCached = overrideService.paused();
        } catch (Exception e) {
            pausedCached = true; // fail closed
        } finally {
            pausedCheckedAt = System.currentTimeMillis();
            pauseRefreshing.set(false);
        }
    }

    public OfferResult offer(OfferInput input, VoucherChatContext ctx) {
        LocalDate day = clock.today();
        String sessionId = ctx == null ? null : trimToNull(ctx.sessionId());
        BigDecimal requested = input.requestedAmount();

        // 1. Switches. Re-read settings: the kill switch takes effect immediately here.
        VoucherSettings settings = settingsService.current();
        if (!featureAllowed || !settings.isEnabled() || !scopeMonitor.ready()) {
            return refuse("DISABLED", "No voucher is available right now. Carry on helping without mentioning discounts.",
                sessionId, day, null, requested, false, false);
        }
        if (overrideService.paused()) {
            return refuse("PAUSED", "No voucher is available right now. Carry on helping without mentioning discounts.",
                sessionId, day, null, requested, false, false);
        }
        if (sessionId == null || sessionId.length() > 255) {
            return refuse("NO_SESSION", "Vouchers can only be offered in the storefront chat.",
                sessionId, day, null, requested, false, false);
        }

        // 2. Normalise the cart. Unknown, malformed or absurd lines are refused, not trusted.
        List<Line> lines;
        try {
            lines = normaliseLines(input.lines());
        } catch (IllegalArgumentException e) {
            return refuse("BAD_CART", e.getMessage(), sessionId, day, null, requested, false, false);
        }

        // 3. Recompute the subtotal from Shopify prices. The model's numbers are never used.
        List<VariantInfo> variants;
        try {
            variants = gateway.fetchVariants(lines.stream().map(Line::variantGid).toList());
        } catch (GatewayException e) {
            log.warn("voucher: variant lookup failed: {}", e.getMessage());
            return refuse("LOOKUP_FAILED", "I couldn't check the cart just now, so no voucher this time.",
                sessionId, day, null, requested, false, false);
        }
        Map<String, VariantInfo> byGid = new LinkedHashMap<>();
        for (VariantInfo v : variants) byGid.put(v.variantGid(), v);

        VoucherExclusions exclusions = VoucherExclusions.parse(settings.getExclusions());
        BigDecimal eligibleSubtotal = BigDecimal.ZERO;
        List<String> eligibleGids = new ArrayList<>();
        List<Line> knownLines = new ArrayList<>();
        int saleOrExcluded = 0;
        List<Map<String, Object>> onSale = new ArrayList<>();
        for (Line line : lines) {
            VariantInfo v = byGid.get(line.variantGid());
            if (v == null) continue;
            knownLines.add(line);
            boolean eligible = v.price() != null && v.price().signum() > 0
                && v.availableForSale()
                && (v.productStatus() == null || "ACTIVE".equalsIgnoreCase(v.productStatus()))
                && !v.onSale()
                && !exclusions.excludes(v.vendor(), v.productType(), v.tags());
            if (eligible) {
                eligibleSubtotal = eligibleSubtotal.add(v.price().multiply(BigDecimal.valueOf(line.quantity())));
                eligibleGids.add(v.variantGid());
            } else {
                saleOrExcluded++;
                if (v.onSale()) onSale.add(saleItem(v, line));
            }
        }
        eligibleSubtotal = eligibleSubtotal.setScale(2, RoundingMode.HALF_UP);
        if (knownLines.isEmpty()) {
            return refuse("BAD_CART", "None of those items were found. Use the variant ids from the product search results.",
                sessionId, day, eligibleSubtotal, requested, false, false);
        }

        // 4. Minimum subtotal (full-price, non-excluded items only).
        if (eligibleSubtotal.compareTo(settings.getMinSubtotal()) < 0) {
            BigDecimal shortBy = settings.getMinSubtotal().subtract(eligibleSubtotal);
            String msg = "No voucher on this cart yet: full-price items total " + VoucherRules.dollars(eligibleSubtotal)
                + (saleOrExcluded > 0 ? " (sale and excluded items don't count)" : "")
                + ". Another " + VoucherRules.dollars(shortBy) + " of full-price items would qualify.";
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("eligible_subtotal", VoucherRules.money(eligibleSubtotal));
            extra.put("amount_needed", VoucherRules.money(shortBy));
            return refuse("BELOW_MINIMUM", msg, sessionId, day, eligibleSubtotal, requested, false, false, extra);
        }

        // 5. Holdout: a fixed share of eligible chats is never offered, to measure lift.
        if (VoucherRules.inHoldout(sessionId, settings.getHoldoutPct())) {
            return refuse("HOLDOUT", "No voucher is available for this order. Carry on helping normally and don't mention discounts.",
                sessionId, day, eligibleSubtotal, requested, true, true);
        }

        // Optional, only for the one-per-customer-per-day limit. Codes aren't locked to it.
        String email = normaliseEmail(input.customerEmail());

        // 6. Amount: positive, $5 steps, capped by 10% (floored to $5), the cart-size tier and (later) the pool.
        if (requested == null || requested.compareTo(VoucherRules.STEP) < 0) {
            return refuse("BAD_AMOUNT", "The voucher amount must be at least $5, in $5 steps.",
                sessionId, day, eligibleSubtotal, requested, true, false);
        }
        Optional<BigDecimal> sessionOverride = overrideService.sessionAmount(sessionId);
        BigDecimal cap;
        BigDecimal pctForMinimum;
        BigDecimal baseMaxForMinimum;
        BigDecimal tierFloorForMinimum;
        if (sessionOverride.isPresent()) {
            // Owner-approved higher amount: still never over the hard caps.
            cap = VoucherRules.min(VoucherRules.min(sessionOverride.get(), VoucherRules.HARD_MAX_AMOUNT),
                VoucherRules.pctCeiling(eligibleSubtotal, VoucherRules.HARD_MAX_PCT));
            pctForMinimum = VoucherRules.HARD_MAX_PCT;
            baseMaxForMinimum = null;
            tierFloorForMinimum = null;
        } else {
            cap = VoucherRules.min(VoucherRules.pctCeiling(eligibleSubtotal, settings.getMaxPct()),
                VoucherRules.tierCap(eligibleSubtotal, settings.getMaxAmount(), settings.getHighTierSubtotal(),
                    settings.getHighTierMaxAmount()));
            pctForMinimum = settings.getMaxPct();
            baseMaxForMinimum = settings.getMaxAmount();
            tierFloorForMinimum = settings.getHighTierSubtotal();
        }
        BigDecimal desired = VoucherRules.floorToStep(VoucherRules.min(requested, cap));
        if (desired.compareTo(VoucherRules.STEP) < 0) {
            return refuse("BAD_AMOUNT", "No voucher is available for this order.",
                sessionId, day, eligibleSubtotal, requested, true, false);
        }

        // 7. Reserve atomically (session/customer/IP limits + pool), then create in Shopify.
        List<String> customerKeys = customerKeys(email, ctx.customerId());
        String ipHash = ipHash(ctx.clientIp());
        Instant now = clock.now();
        Instant expiresAt = now.plusSeconds(60L * settings.getValidityMinutes());
        BigDecimal poolLimit = settings.getDailyPool().add(overrideService.poolRaise(day));
        String code = VoucherRules.generateCode();

        VoucherStore.Reservation reservation;
        try {
            reservation = store.reserve(new VoucherStore.ReserveRequest(code, desired, poolLimit, day, sessionId,
                customerKeys, ipHash, settings.getMaxPerIpPerDay(), eligibleSubtotal, pctForMinimum,
                settings.getMinSubtotal(), baseMaxForMinimum, tierFloorForMinimum, input.reason(), now, expiresAt));
        } catch (DataIntegrityViolationException | jakarta.persistence.PersistenceException e) {
            // Lost a race with a concurrent issue for the same session or customer.
            return refuse("ALREADY_ISSUED", "This chat has already had its voucher.",
                sessionId, day, eligibleSubtotal, requested, true, false);
        }
        switch (reservation.status()) {
            case SESSION_USED, CUSTOMER_USED, IP_LIMIT -> {
                return refuse("ALREADY_ISSUED", "A voucher has already been given for this customer today, so I can't add another.",
                    sessionId, day, eligibleSubtotal, requested, true, false);
            }
            case POOL_EMPTY -> {
                return refuse("UNAVAILABLE", "No voucher is available right now. Carry on helping without mentioning discounts.",
                    sessionId, day, eligibleSubtotal, requested, true, false);
            }
            default -> { }
        }

        VoucherLedgerEntry entry = reservation.entry();
        CreateResult created = null;
        String error;
        try {
            created = gateway.createCode(new CodeSpec(code, "Chat voucher " + code, entry.getAmount(),
                entry.getMinSubtotal(), eligibleGids, now, expiresAt));
            error = created.ok() ? null : created.error();
        } catch (GatewayException e) {
            error = e.getMessage();
            if (isAccessDenied(error)) scopeMonitor.markMissing(error);
        }

        if (created != null && created.ok() && store.markIssued(entry.getId(), created.discountId())) {
            log.info("VOUCHER_ISSUED id={} code={} amount={} subtotal={} min={} session={} expires={}",
                entry.getId(), mask(code), entry.getAmount(), eligibleSubtotal, entry.getMinSubtotal(),
                sessionId, expiresAt);
            recordOffer(sessionId, day, eligibleSubtotal, requested, "ISSUED", true, false, entry.getId(), input.reason());
            return issuedResult(entry, knownLines, requested, onSale);
        }

        if (created != null && created.accessDenied()) scopeMonitor.markMissing(error);
        log.warn("voucher: Shopify create failed for ledger {}: {}", entry.getId(), error);
        cleanUpFailedCreate(entry);
        return refuse("CREATE_FAILED", "I couldn't create a voucher just now, so no voucher this time.",
            sessionId, day, eligibleSubtotal, requested, true, false);
    }

    /**
     * The create may or may not have happened (timeouts, client retries). Return the
     * value only once Shopify confirms no live code exists; otherwise leave the row
     * PENDING for the reconcile job.
     */
    private void cleanUpFailedCreate(VoucherLedgerEntry entry) {
        try {
            CodeState state = gateway.lookupCode(entry.getCode());
            if (state.found() && !gateway.deleteCode(state.discountId())) {
                store.recordAttempt(entry.getId(), "create failed; delete of stray code failed");
                return;
            }
            store.releaseTo(entry, VoucherStatus.PENDING, VoucherStatus.FAILED, clock.now());
        } catch (Exception e) {
            store.recordAttempt(entry.getId(), "create failed; cleanup deferred: " + e.getMessage());
        }
    }

    private static boolean isAccessDenied(String error) {
        String e = error == null ? "" : error.toLowerCase(Locale.ROOT);
        return e.contains("access denied") || e.contains("write_discounts");
    }

    private static Map<String, Object> saleItem(VariantInfo v, Line line) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("variant_id", line.numericId());
        m.put("title", v.title());
        m.put("quantity", line.quantity());
        m.put("price", VoucherRules.money(v.price()));
        m.put("compare_at_price", VoucherRules.money(v.compareAtPrice()));
        return m;
    }

    private OfferResult issuedResult(VoucherLedgerEntry entry, List<Line> lines, BigDecimal requested,
                                     List<Map<String, Object>> onSale) {
        String amount = VoucherRules.dollars(entry.getAmount());
        String until = LOCAL_TIME.format(clock.local(entry.getExpiresAt())).toLowerCase(Locale.ENGLISH)
            .replace(" am", "am").replace(" pm", "pm");
        StringBuilder cart = new StringBuilder("https://").append(shopUrl).append("/cart/");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) cart.append(',');
            cart.append(lines.get(i).numericId()).append(':').append(lines.get(i).quantity());
        }
        cart.append("?discount=").append(entry.getCode());

        Map<String, Object> f = new LinkedHashMap<>();
        f.put("code", entry.getCode());
        f.put("amount", VoucherRules.money(entry.getAmount()));
        f.put("currency", "AUD");
        f.put("expires_at", entry.getExpiresAt().toString());
        f.put("expires_local", until);
        f.put("minimum_spend", VoucherRules.money(entry.getMinSubtotal()));
        f.put("cart_link", cart.toString());
        if (!onSale.isEmpty()) f.put("already_discounted_items", onSale);
        StringBuilder note = new StringBuilder("Give the customer the code ").append(entry.getCode())
            .append(" (").append(amount).append(" off, single use, valid until ").append(until)
            .append(") and the cart link, which applies it automatically. It needs ")
            .append(VoucherRules.dollars(entry.getMinSubtotal()))
            .append(" of the full-price items in this cart and can't be combined with other discounts.");
        if (!onSale.isEmpty()) {
            note.append(" The items in already_discounted_items are already on sale, so the voucher doesn't apply to them:")
                .append(" point that out, and that the voucher comes off the rest of the order.");
        }
        if (requested != null && requested.compareTo(entry.getAmount()) > 0) {
            note.append(" You asked for ").append(VoucherRules.dollars(requested.setScale(2, RoundingMode.DOWN)))
                .append(" but the most for this order is ").append(amount)
                .append(". If the customer asks for more, say \"I can't go higher than ").append(amount)
                .append(" on this order.\"");
        }
        f.put("instructions", note.toString());
        return new OfferResult("issued", f);
    }

    private OfferResult refuse(String code, String message, String sessionId, LocalDate day, BigDecimal subtotal,
                               BigDecimal requested, boolean eligible, boolean holdout) {
        return refuse(code, message, sessionId, day, subtotal, requested, eligible, holdout, Map.of());
    }

    private OfferResult refuse(String code, String message, String sessionId, LocalDate day, BigDecimal subtotal,
                               BigDecimal requested, boolean eligible, boolean holdout, Map<String, Object> extra) {
        recordOffer(sessionId, day, subtotal, requested, code, eligible, holdout, null, null);
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("reason_code", code);
        f.put("message", message);
        f.putAll(extra);
        return new OfferResult("refused", f);
    }

    private void recordOffer(String sessionId, LocalDate day, BigDecimal subtotal, BigDecimal requested,
                             String outcome, boolean eligible, boolean holdout, Long ledgerId, String reason) {
        try {
            store.recordOffer(VoucherOffer.builder()
                .sessionId(VoucherStore.truncate(sessionId, 255)).poolDate(day).cartSubtotal(subtotal)
                .requested(clampForColumn(requested)).outcome(outcome).eligible(eligible).holdout(holdout)
                .ledgerId(ledgerId).reason(VoucherStore.truncate(reason, 500)).createdAt(clock.now()).build());
        } catch (Exception e) {
            log.debug("voucher: could not record offer: {}", e.getMessage());
        }
    }

    private static BigDecimal clampForColumn(BigDecimal v) {
        if (v == null) return null;
        BigDecimal max = new BigDecimal("99999999.99");
        if (v.compareTo(max) > 0) return max;
        if (v.compareTo(max.negate()) < 0) return max.negate();
        return v.setScale(2, RoundingMode.DOWN);
    }

    private static List<Line> normaliseLines(List<RawLine> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("I need the cart items (variant ids and quantities) to check a voucher.");
        }
        if (raw.size() > VoucherRules.MAX_LINES) throw new IllegalArgumentException("That cart has too many lines for a voucher.");
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (RawLine l : raw) {
            Matcher m = l == null || l.variantId() == null ? null : VARIANT_ID.matcher(l.variantId().trim());
            if (m == null || !m.matches()) {
                throw new IllegalArgumentException("Use the exact variant ids from the product search results.");
            }
            BigDecimal q = l.quantity();
            if (q == null || q.signum() <= 0 || q.stripTrailingZeros().scale() > 0
                    || q.compareTo(BigDecimal.valueOf(VoucherRules.MAX_QTY_PER_LINE)) > 0) {
                throw new IllegalArgumentException("Quantities must be whole numbers from 1 to " + VoucherRules.MAX_QTY_PER_LINE + ".");
            }
            merged.merge(m.group(1), q.intValueExact(), Integer::sum);
        }
        List<Line> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : merged.entrySet()) {
            if (e.getValue() > VoucherRules.MAX_QTY_PER_LINE) {
                throw new IllegalArgumentException("Quantities must be whole numbers from 1 to " + VoucherRules.MAX_QTY_PER_LINE + ".");
            }
            out.add(new Line(VARIANT_GID_PREFIX + e.getKey(), e.getKey(), e.getValue()));
        }
        return out;
    }

    /** Lower-cased, trimmed email if it passes the strict pattern, else null. */
    static String normaliseEmail(String raw) {
        if (raw == null) return null;
        String e = raw.trim().toLowerCase(Locale.ROOT);
        return e.length() <= 254 && EMAIL.matcher(e).matches() ? e : null;
    }

    /**
     * The one-per-day key for an email: aliases of one inbox map to the same key
     * ("+tag" suffixes anywhere; dots and googlemail.com for Gmail).
     */
    static String inboxKey(String email) {
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        int plus = local.indexOf('+');
        if (plus > 0) local = local.substring(0, plus);
        if (domain.equals("googlemail.com")) domain = "gmail.com";
        if (domain.equals("gmail.com")) local = local.replace(".", "");
        return local + "@" + domain;
    }

    /** email must already be normalised (or null). */
    static List<String> customerKeys(String email, String customerId) {
        List<String> keys = new ArrayList<>();
        if (email != null) keys.add(VoucherRules.sha256("email:" + inboxKey(email)));
        String id = trimToNull(customerId);
        if (id != null && id.length() <= 255) keys.add(VoucherRules.sha256("customer:" + id));
        return keys;
    }

    /**
     * A missing or unusable IP is one shared "unknown" bucket that is still limited,
     * so hiding the address can't skip the per-IP limit.
     */
    static String ipHash(String ip) {
        String v = trimToNull(ip);
        if (v == null || "unknown".equalsIgnoreCase(v) || v.length() > 64) {
            return VoucherRules.sha256("ip:unknown");
        }
        // IPv6 counts per /64, so rotating within one subscriber's prefix doesn't help.
        return VoucherRules.sha256("ip:" + com.shopify.api.util.ClientIpResolver.limitKey(v));
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** Logs show only the first 4 characters of a code. */
    static String mask(String code) {
        return code == null || code.length() < 6 ? "****" : code.substring(0, 4) + "…";
    }
}
