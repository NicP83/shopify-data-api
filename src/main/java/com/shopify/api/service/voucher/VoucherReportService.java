package com.shopify.api.service.voucher;

import com.shopify.api.model.voucher.VoucherLedgerEntry;
import com.shopify.api.model.voucher.VoucherOffer;
import com.shopify.api.model.voucher.VoucherSettings;
import com.shopify.api.model.voucher.VoucherStatus;
import com.shopify.api.repository.voucher.VoucherLedgerRepository;
import com.shopify.api.repository.voucher.VoucherOfferRepository;
import com.shopify.api.service.voucher.VoucherShopifyGateway.OrderInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Daily numbers: offered, issued, redeemed, expired, dollars, AOV with vs without a voucher, holdout. */
@Service
@Slf4j
public class VoucherReportService {

    private final VoucherLedgerRepository ledgerRepository;
    private final VoucherOfferRepository offerRepository;
    private final VoucherStore store;
    private final VoucherSettingsService settingsService;
    private final VoucherOverrideService overrideService;
    private final VoucherShopifyGateway gateway;
    private final VoucherClock clock;

    public VoucherReportService(VoucherLedgerRepository ledgerRepository, VoucherOfferRepository offerRepository,
                                VoucherStore store, VoucherSettingsService settingsService,
                                VoucherOverrideService overrideService, VoucherShopifyGateway gateway,
                                VoucherClock clock) {
        this.ledgerRepository = ledgerRepository;
        this.offerRepository = offerRepository;
        this.store = store;
        this.settingsService = settingsService;
        this.overrideService = overrideService;
        this.gateway = gateway;
        this.clock = clock;
    }

    public Map<String, Object> report(LocalDate day, boolean includeStoreAov) {
        List<VoucherLedgerEntry> ledger = ledgerRepository.findByPoolDateOrderByIssuedAtDesc(day);
        List<VoucherOffer> offers = offerRepository.findByPoolDate(day);
        VoucherSettings settings = settingsService.current();

        Set<String> offeredSessions = new HashSet<>();
        Set<String> holdoutSessions = new HashSet<>();
        Map<String, Integer> outcomes = new TreeMap<>();
        for (VoucherOffer o : offers) {
            outcomes.merge(o.getOutcome(), 1, Integer::sum);
            if (o.getSessionId() == null) continue;
            if (o.isHoldout()) holdoutSessions.add(o.getSessionId());
            else if (o.isEligible()) offeredSessions.add(o.getSessionId());
        }

        int issued = 0, redeemed = 0, expired = 0, revoked = 0, outstanding = 0, failed = 0;
        BigDecimal spent = BigDecimal.ZERO, outstandingValue = BigDecimal.ZERO, returned = BigDecimal.ZERO;
        BigDecimal voucherOrderSum = BigDecimal.ZERO;
        int voucherOrders = 0;
        Set<String> voucherOrderIds = new HashSet<>();
        for (VoucherLedgerEntry e : ledger) {
            VoucherStatus s = e.getStatus();
            if (s == VoucherStatus.FAILED) { failed++; continue; }
            if (s != VoucherStatus.PENDING) issued++;
            switch (s) {
                case REDEEMED -> {
                    redeemed++;
                    spent = spent.add(e.getAmount());
                    if (e.getOrderId() != null) voucherOrderIds.add(e.getOrderId());
                    if (e.getOrderTotal() != null) {
                        voucherOrderSum = voucherOrderSum.add(e.getOrderTotal());
                        voucherOrders++;
                    }
                }
                case EXPIRED -> { expired++; returned = returned.add(e.getAmount()); }
                case REVOKED -> { revoked++; returned = returned.add(e.getAmount()); }
                case ISSUED, PENDING -> { outstanding++; outstandingValue = outstandingValue.add(e.getAmount()); }
                default -> { }
            }
        }

        BigDecimal limit = settings.getDailyPool().add(overrideService.poolRaise(day));
        BigDecimal reserved = store.reserved(day);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("date", day.toString());
        r.put("enabled", settings.isEnabled());
        r.put("pool_limit", VoucherRules.money(limit));
        r.put("pool_reserved", VoucherRules.money(reserved));
        r.put("pool_remaining", VoucherRules.money(limit.subtract(reserved).max(BigDecimal.ZERO)));
        r.put("offered_sessions", offeredSessions.size());
        r.put("holdout_sessions", holdoutSessions.size());
        r.put("issued", issued);
        r.put("redeemed", redeemed);
        r.put("expired", expired);
        r.put("revoked", revoked);
        r.put("outstanding", outstanding);
        r.put("failed_creates", failed);
        r.put("dollars_spent", VoucherRules.money(spent));
        r.put("dollars_outstanding", VoucherRules.money(outstandingValue));
        r.put("dollars_returned", VoucherRules.money(returned));
        r.put("redemption_rate", issued == 0 ? null : ratio(redeemed, issued));
        r.put("aov_with_voucher", voucherOrders == 0 ? null : VoucherRules.money(
            voucherOrderSum.divide(BigDecimal.valueOf(voucherOrders), 2, RoundingMode.HALF_UP)));
        r.put("voucher_orders_with_total", voucherOrders);
        r.put("outcomes", outcomes);

        if (includeStoreAov) {
            try {
                List<OrderInfo> orders = gateway.ordersBetween(clock.startOfDay(day), clock.endOfDay(day));
                BigDecimal sum = BigDecimal.ZERO;
                int n = 0;
                for (OrderInfo o : orders) {
                    if (o.total() == null || voucherOrderIds.contains(o.orderId())) continue;
                    sum = sum.add(o.total());
                    n++;
                }
                r.put("aov_without_voucher", n == 0 ? null
                    : VoucherRules.money(sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP)));
                r.put("store_orders_without_voucher", n);
                r.put("store_orders_truncated", orders.size() >= 250);
            } catch (Exception e) {
                log.warn("voucher: store AOV lookup failed: {}", e.getMessage());
                r.put("aov_without_voucher", null);
                r.put("aov_without_voucher_error", "Shopify lookup failed");
            }
        }
        return r;
    }

    private static String ratio(int a, int b) {
        return BigDecimal.valueOf(a).divide(BigDecimal.valueOf(b), 3, RoundingMode.HALF_UP).toPlainString();
    }
}
