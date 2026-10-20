package com.shopify.api.service.voucher;

import com.shopify.api.model.voucher.VoucherSettings;
import com.shopify.api.repository.voucher.VoucherSettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loads, validates and saves the voucher settings row. A missing row (fresh DB,
 * or V024 not yet applied) reads as the disabled defaults.
 */
@Service
@Slf4j
public class VoucherSettingsService {

    private static final long CACHE_TTL_MS = 15_000;

    private final VoucherSettingsRepository repository;
    private volatile VoucherSettings cached;
    private volatile long cachedAt;
    private final AtomicBoolean refreshing = new AtomicBoolean();

    public VoucherSettingsService(VoucherSettingsRepository repository) {
        this.repository = repository;
    }

    /** Fresh from the DB; used wherever a decision is made (issuing, admin). */
    public VoucherSettings current() {
        try {
            VoucherSettings s = repository.findById(VoucherSettings.SINGLETON_ID).orElseGet(VoucherSettings::defaults);
            cached = s;
            cachedAt = System.currentTimeMillis();
            return s;
        } catch (Exception e) {
            // Fail closed: if the table can't be read, vouchers are off.
            log.warn("voucher: settings unreadable, treating as disabled: {}", e.getMessage());
            return VoucherSettings.defaults();
        }
    }

    /**
     * For the per-turn "is the tool offered" check only; issuing re-reads. Never touches
     * the DB on the caller's thread (it may be a reactor event loop): a stale value is
     * refreshed in the background, and until the first load it reads as disabled.
     */
    public VoucherSettings cached() {
        VoucherSettings s = cached;
        if (s == null || System.currentTimeMillis() - cachedAt > CACHE_TTL_MS) {
            refreshInBackground();
        }
        return s != null ? s : VoucherSettings.defaults();
    }

    private void refreshInBackground() {
        if (refreshing.compareAndSet(false, true)) {
            Schedulers.boundedElastic().schedule(() -> {
                try {
                    current();
                } finally {
                    refreshing.set(false);
                }
            });
        }
    }

    /** Throws IllegalArgumentException naming every out-of-range field. */
    public static void validate(VoucherSettings s) {
        List<String> errors = new ArrayList<>();
        if (s.getDailyPool() == null || s.getDailyPool().signum() < 0
                || s.getDailyPool().compareTo(VoucherRules.HARD_MAX_POOL) > 0) {
            errors.add("dailyPool must be 0.." + VoucherRules.HARD_MAX_POOL);
        }
        if (s.getMaxPct() == null || s.getMaxPct().signum() <= 0
                || s.getMaxPct().compareTo(VoucherRules.HARD_MAX_SETTINGS_PCT) > 0) {
            errors.add("maxPct must be >0 and <= " + VoucherRules.HARD_MAX_SETTINGS_PCT);
        }
        if (s.getMaxAmount() == null || s.getMaxAmount().compareTo(VoucherRules.STEP) < 0
                || s.getMaxAmount().compareTo(VoucherRules.HARD_MAX_AMOUNT) > 0
                || VoucherRules.floorToStep(s.getMaxAmount()).compareTo(s.getMaxAmount()) != 0) {
            errors.add("maxAmount must be a multiple of 5 between 5 and " + VoucherRules.HARD_MAX_AMOUNT);
        }
        if (s.getMinSubtotal() == null || s.getMinSubtotal().compareTo(VoucherRules.HARD_MIN_SUBTOTAL) < 0
                || s.getMinSubtotal().compareTo(new BigDecimal("100000")) > 0) {
            errors.add("minSubtotal must be at least " + VoucherRules.HARD_MIN_SUBTOTAL);
        }
        if (s.getValidityMinutes() < VoucherRules.MIN_VALIDITY_MINUTES
                || s.getValidityMinutes() > VoucherRules.HARD_MAX_VALIDITY_MINUTES) {
            errors.add("validityMinutes must be " + VoucherRules.MIN_VALIDITY_MINUTES + ".."
                + VoucherRules.HARD_MAX_VALIDITY_MINUTES);
        }
        if (s.getHoldoutPct() < 0 || s.getHoldoutPct() > 100) errors.add("holdoutPct must be 0..100");
        if (s.getMaxPerIpPerDay() < 1 || s.getMaxPerIpPerDay() > 20) errors.add("maxPerIpPerDay must be 1..20");
        try {
            VoucherExclusions.parse(s.getExclusions());
        } catch (IllegalArgumentException e) {
            errors.add(e.getMessage());
        }
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
    }

    /**
     * The changes in {@code next} that loosen a limit compared with {@code prev}
     * (more money can go out). These need the owner key; tightening needs only admin.
     */
    public static List<String> loosenings(VoucherSettings prev, VoucherSettings next) {
        List<String> out = new ArrayList<>();
        if (!prev.isEnabled() && next.isEnabled()) out.add("enabled");
        if (next.getDailyPool().compareTo(prev.getDailyPool()) > 0) out.add("dailyPool");
        if (next.getMaxPct().compareTo(prev.getMaxPct()) > 0) out.add("maxPct");
        if (next.getMaxAmount().compareTo(prev.getMaxAmount()) > 0) out.add("maxAmount");
        if (next.getMinSubtotal().compareTo(prev.getMinSubtotal()) < 0) out.add("minSubtotal");
        if (next.getValidityMinutes() > prev.getValidityMinutes()) out.add("validityMinutes");
        if (next.getHoldoutPct() < prev.getHoldoutPct()) out.add("holdoutPct");
        if (next.getMaxPerIpPerDay() > prev.getMaxPerIpPerDay()) out.add("maxPerIpPerDay");
        VoucherExclusions a = VoucherExclusions.parse(prev.getExclusions());
        VoucherExclusions b = VoucherExclusions.parse(next.getExclusions());
        if (!b.vendors().containsAll(a.vendors()) || !b.productTypes().containsAll(a.productTypes())
                || !b.tags().containsAll(a.tags())) {
            out.add("exclusions (removed entries)");
        }
        return out;
    }

    @Transactional
    public VoucherSettings save(VoucherSettings next) {
        validate(next);
        next.setId(VoucherSettings.SINGLETON_ID);
        VoucherSettings saved = repository.save(next);
        cached = saved;
        cachedAt = System.currentTimeMillis();
        return saved;
    }

    /** Kill switch: needs only the admin key. */
    @Transactional
    public VoucherSettings disable(String by) {
        VoucherSettings s = current().toBuilder().enabled(false).updatedBy(by).updatedAt(java.time.Instant.now()).build();
        return save(s);
    }
}
