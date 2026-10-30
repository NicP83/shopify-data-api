package com.shopify.api.service.voucher;

import com.shopify.api.model.voucher.VoucherLedgerEntry;
import com.shopify.api.model.voucher.VoucherStatus;
import com.shopify.api.repository.voucher.VoucherLedgerRepository;
import com.shopify.api.service.voucher.VoucherShopifyGateway.CodeState;
import com.shopify.api.service.voucher.VoucherShopifyGateway.OrderInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Settles codes after they expire. An ISSUED code is looked at once its
 * expiry plus a 5-minute grace has passed (so ~65 minutes after issue):
 * used -> REDEEMED (stays spent); unused -> deleted in Shopify, EXPIRED, value
 * returned to its day's pool. Stale PENDING rows (a create whose outcome was
 * unknown) are settled the same way. Value is returned only once Shopify
 * confirms the code is gone; otherwise the row is retried on the next pass.
 */
@Service
@Slf4j
public class VoucherReconcileService {

    public static final Duration GRACE = Duration.ofMinutes(5);
    static final Duration PENDING_STALE = Duration.ofMinutes(5);
    static final int ALERT_AFTER_ATTEMPTS = 10;

    public record Summary(int redeemed, int expired, int failed, int deferred) {}

    private final VoucherLedgerRepository ledgerRepository;
    private final VoucherStore store;
    private final VoucherShopifyGateway gateway;
    private final VoucherClock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    public VoucherReconcileService(VoucherLedgerRepository ledgerRepository, VoucherStore store,
                                   VoucherShopifyGateway gateway, VoucherClock clock) {
        this.ledgerRepository = ledgerRepository;
        this.store = store;
        this.gateway = gateway;
        this.clock = clock;
    }

    /** One pass. Overlapping calls are skipped (and the status CAS makes them harmless anyway). */
    public Summary reconcile() {
        if (!running.compareAndSet(false, true)) return new Summary(0, 0, 0, 0);
        int redeemed = 0, expired = 0, failed = 0, deferred = 0;
        try {
            Instant now = clock.now();
            List<VoucherLedgerEntry> due = ledgerRepository.findByStatusAndExpiresAtBefore(VoucherStatus.ISSUED, now.minus(GRACE));
            for (VoucherLedgerEntry e : due) {
                switch (settleIssued(e)) {
                    case REDEEMED -> redeemed++;
                    case EXPIRED -> expired++;
                    default -> deferred++;
                }
            }
            List<VoucherLedgerEntry> stale = ledgerRepository.findByStatusAndIssuedAtBefore(VoucherStatus.PENDING, now.minus(PENDING_STALE));
            for (VoucherLedgerEntry e : stale) {
                if (settlePending(e)) failed++;
                else deferred++;
            }
            if (redeemed + expired + failed + deferred > 0) {
                log.info("VOUCHER_RECONCILE redeemed={} expired={} failed={} deferred={}", redeemed, expired, failed, deferred);
            }
        } catch (Exception e) {
            log.warn("voucher: reconcile pass failed: {}", e.getMessage());
        } finally {
            running.set(false);
        }
        return new Summary(redeemed, expired, failed, deferred);
    }

    private VoucherStatus settleIssued(VoucherLedgerEntry e) {
        try {
            // The discount's own usage count needs only read_discounts, so it decides used-or-not.
            // The order search (read_orders) only adds the order id and total, and is optional:
            // without that scope, codes must still settle instead of staying ISSUED forever.
            CodeState state = gateway.lookupCode(e.getCode());
            Optional<OrderInfo> order = findOrderQuietly(e.getCode());
            if (order.isPresent()) {
                store.markRedeemed(e.getId(), order.get().orderId(), order.get().total(), clock.now());
                return VoucherStatus.REDEEMED;
            }
            if (state.found() && state.usageCount() > 0) {
                // Used; the order isn't searchable (not yet, or no read_orders): it stays spent.
                store.markRedeemed(e.getId(), null, null, clock.now());
                return VoucherStatus.REDEEMED;
            }
            if (state.found() && !gateway.deleteCode(state.discountId())) {
                noteFailure(e, "delete failed");
                return VoucherStatus.ISSUED;
            }
            store.releaseTo(e, VoucherStatus.ISSUED, VoucherStatus.EXPIRED, clock.now());
            return VoucherStatus.EXPIRED;
        } catch (Exception ex) {
            noteFailure(e, ex.getMessage());
            return VoucherStatus.ISSUED;
        }
    }

    private Optional<OrderInfo> findOrderQuietly(String code) {
        try {
            return gateway.findOrderByCode(code);
        } catch (Exception ex) {
            log.debug("voucher: order lookup unavailable for a code ({}); using the usage count", ex.getMessage());
            return Optional.empty();
        }
    }

    /** A create whose outcome was never known: remove any stray code, then return the value. */
    private boolean settlePending(VoucherLedgerEntry e) {
        try {
            CodeState state = gateway.lookupCode(e.getCode());
            if (state.found()) {
                if (state.usageCount() > 0) {
                    // Somehow used (the customer did get it): treat as spent.
                    store.markIssued(e.getId(), state.discountId());
                    store.markRedeemed(e.getId(), null, null, clock.now());
                    return false;
                }
                if (!gateway.deleteCode(state.discountId())) {
                    noteFailure(e, "pending: delete failed");
                    return false;
                }
            }
            return store.releaseTo(e, VoucherStatus.PENDING, VoucherStatus.FAILED, clock.now());
        } catch (Exception ex) {
            noteFailure(e, "pending: " + ex.getMessage());
            return false;
        }
    }

    private void noteFailure(VoucherLedgerEntry e, String error) {
        store.recordAttempt(e.getId(), error);
        int attempts = e.getReconcileAttempts() + 1;
        if (attempts >= ALERT_AFTER_ATTEMPTS && attempts % ALERT_AFTER_ATTEMPTS == 0) {
            log.error("voucher: ledger {} still unsettled after {} attempts ({}); value stays reserved",
                e.getId(), attempts, error);
        }
    }

    /** Admin cancel of a live code: delete it in Shopify, then return the value. */
    public boolean revoke(VoucherLedgerEntry e) {
        if (e.getStatus() != VoucherStatus.ISSUED) return false;
        CodeState state = gateway.lookupCode(e.getCode());
        if (state.found() && state.usageCount() > 0) return false;
        if (state.found() && !gateway.deleteCode(state.discountId())) return false;
        return store.releaseTo(e, VoucherStatus.ISSUED, VoucherStatus.REVOKED, clock.now());
    }
}
