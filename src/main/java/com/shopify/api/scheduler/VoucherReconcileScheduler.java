package com.shopify.api.scheduler;

import com.shopify.api.service.voucher.VoucherReconcileService;
import com.shopify.api.service.voucher.VoucherScopeMonitor;
import com.shopify.api.service.voucher.VoucherSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every minute: settle codes whose expiry + 5 min grace has passed (so each code
 * is handled ~65 min after issue), and, while vouchers are enabled, keep the
 * write_discounts scope check fresh. Also runs once at startup so codes that
 * expired while the app was down are settled promptly.
 * With no ledger rows and vouchers disabled this makes no Shopify calls.
 */
@Component
@Slf4j
public class VoucherReconcileScheduler {

    private final VoucherReconcileService reconcileService;
    private final VoucherScopeMonitor scopeMonitor;
    private final VoucherSettingsService settingsService;

    public VoucherReconcileScheduler(VoucherReconcileService reconcileService, VoucherScopeMonitor scopeMonitor,
                                     VoucherSettingsService settingsService) {
        this.reconcileService = reconcileService;
        this.scopeMonitor = scopeMonitor;
        this.settingsService = settingsService;
    }

    @Scheduled(fixedDelayString = "${vouchers.reconcile-interval-ms:60000}", initialDelay = 90_000)
    public void tick() {
        try {
            if (settingsService.current().isEnabled()) scopeMonitor.refreshIfDue();
            reconcileService.reconcile();
        } catch (Exception e) {
            log.warn("voucher: scheduled reconcile failed: {}", e.getMessage());
        }
    }

    /** Startup pass, off the main thread so a slow Shopify can't delay boot. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        Thread t = new Thread(() -> {
            try {
                if (settingsService.current().isEnabled()) scopeMonitor.refresh();
                VoucherReconcileService.Summary s = reconcileService.reconcile();
                log.info("voucher: startup reconcile done ({} redeemed, {} expired, {} failed, {} deferred)",
                    s.redeemed(), s.expired(), s.failed(), s.deferred());
            } catch (Exception e) {
                log.warn("voucher: startup reconcile failed: {}", e.getMessage());
            }
        }, "voucher-startup-reconcile");
        t.setDaemon(true);
        t.start();
    }
}
