package com.shopify.api.service.voucher;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Tracks the Shopify token's access scopes. Vouchers need write_discounts, and
 * write_customers while codes are locked to an email. Until a check has confirmed
 * them, the offer_discount tool stays hidden (fail closed). Checks run only while
 * vouchers are enabled, so a disabled feature makes no Shopify calls.
 */
@Component
@Slf4j
public class VoucherScopeMonitor {

    public static final String WRITE_DISCOUNTS = "write_discounts";
    public static final String WRITE_CUSTOMERS = "write_customers";

    static final long RECHECK_OK_MS = 30 * 60_000L;
    static final long RECHECK_BAD_MS = 5 * 60_000L;

    private final VoucherShopifyGateway gateway;
    private volatile Set<String> granted; // null = not known yet
    private volatile long checkedAt;

    public VoucherScopeMonitor(VoucherShopifyGateway gateway) {
        this.gateway = gateway;
    }

    /** Whether the scopes needed right now are confirmed. */
    public boolean ready(boolean customersNeeded) {
        Set<String> g = granted;
        return g != null && g.contains(WRITE_DISCOUNTS) && (!customersNeeded || g.contains(WRITE_CUSTOMERS));
    }

    /** null = unknown, else whether the scope is granted. For the admin page. */
    public Boolean state(String scope) {
        Set<String> g = granted;
        return g == null ? null : g.contains(scope);
    }

    /** Re-check when unknown or stale. Never throws. */
    public void refreshIfDue() {
        long age = System.currentTimeMillis() - checkedAt;
        Set<String> g = granted;
        boolean complete = g != null && g.contains(WRITE_DISCOUNTS) && g.contains(WRITE_CUSTOMERS);
        boolean due = g == null ? age > 60_000L || checkedAt == 0
            : complete ? age > RECHECK_OK_MS : age > RECHECK_BAD_MS;
        if (due) refresh();
    }

    public void refresh() {
        try {
            checkedAt = System.currentTimeMillis();
            Set<String> result = gateway.grantedScopes().orElse(null);
            if (result == null) {
                log.warn("voucher: could not confirm Shopify scopes; offer_discount stays disabled");
            } else {
                if (!result.contains(WRITE_DISCOUNTS)) {
                    log.error("voucher: Shopify token lacks write_discounts; offer_discount is DISABLED. "
                        + "Reinstall the app with write_discounts (docs/vouchers/VOUCHERS.md).");
                }
                if (!result.contains(WRITE_CUSTOMERS)) {
                    log.warn("voucher: Shopify token lacks write_customers; vouchers that require an email are DISABLED");
                }
            }
            granted = result == null ? null : Set.copyOf(result);
        } catch (Exception e) {
            log.warn("voucher: scope check error: {}", e.getMessage());
        }
    }

    /** Shopify refused a call for access: disable until the next successful check. */
    public void markMissing(String why) {
        log.error("voucher: Shopify denied access ({}); offer_discount is DISABLED", why);
        granted = Set.of();
        checkedAt = System.currentTimeMillis();
    }

    /** Tests only. */
    public void set(Set<String> scopes) {
        granted = scopes == null ? null : Set.copyOf(scopes);
        checkedAt = System.currentTimeMillis();
    }
}
