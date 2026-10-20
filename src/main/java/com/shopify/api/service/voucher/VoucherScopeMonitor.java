package com.shopify.api.service.voucher;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tracks whether the Shopify token has write_discounts. Until a check has
 * confirmed it, the offer_discount tool stays hidden (fail closed). Checks run
 * only while vouchers are enabled, so a disabled feature makes no Shopify calls.
 */
@Component
@Slf4j
public class VoucherScopeMonitor {

    static final long RECHECK_OK_MS = 30 * 60_000L;
    static final long RECHECK_BAD_MS = 5 * 60_000L;

    private final VoucherShopifyGateway gateway;
    private volatile Boolean hasScope; // null = not known yet
    private volatile long checkedAt;

    public VoucherScopeMonitor(VoucherShopifyGateway gateway) {
        this.gateway = gateway;
    }

    public boolean ready() {
        return Boolean.TRUE.equals(hasScope);
    }

    /** null = unknown, else whether write_discounts is granted. */
    public Boolean state() {
        return hasScope;
    }

    /** Re-check when unknown or stale. Never throws. */
    public void refreshIfDue() {
        long age = System.currentTimeMillis() - checkedAt;
        boolean due = hasScope == null ? age > 60_000L || checkedAt == 0
            : hasScope ? age > RECHECK_OK_MS : age > RECHECK_BAD_MS;
        if (due) refresh();
    }

    public void refresh() {
        try {
            checkedAt = System.currentTimeMillis();
            Boolean result = gateway.hasWriteDiscountsScope().orElse(null);
            if (result == null) {
                log.warn("voucher: could not confirm the write_discounts scope; offer_discount stays disabled");
            } else if (!result) {
                log.error("voucher: Shopify token lacks write_discounts; offer_discount is DISABLED. "
                    + "Reinstall the app with write_discounts (docs/vouchers/VOUCHERS.md).");
            } else if (!Boolean.TRUE.equals(hasScope)) {
                log.info("voucher: write_discounts scope confirmed");
            }
            hasScope = result;
        } catch (Exception e) {
            log.warn("voucher: scope check error: {}", e.getMessage());
        }
    }

    /** A create was refused for access: disable until the next successful check. */
    public void markMissing(String why) {
        log.error("voucher: Shopify denied discount creation ({}); offer_discount is DISABLED", why);
        hasScope = false;
        checkedAt = System.currentTimeMillis();
    }

    /** Tests only. */
    public void set(Boolean value) {
        hasScope = value;
        checkedAt = System.currentTimeMillis();
    }
}
