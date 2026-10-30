package com.shopify.api.service.voucher;

import com.shopify.api.model.voucher.VoucherLedgerEntry;
import com.shopify.api.model.voucher.VoucherOffer;
import com.shopify.api.model.voucher.VoucherOverride;
import com.shopify.api.model.voucher.VoucherSettings;
import com.shopify.api.model.voucher.VoucherStatus;
import com.shopify.api.repository.voucher.VoucherClaimRepository;
import com.shopify.api.repository.voucher.VoucherLedgerRepository;
import com.shopify.api.repository.voucher.VoucherOfferRepository;
import com.shopify.api.repository.voucher.VoucherOverrideRepository;
import com.shopify.api.repository.voucher.VoucherPoolDayRepository;
import com.shopify.api.repository.voucher.VoucherSettingsRepository;
import com.shopify.api.service.voucher.VoucherService.OfferInput;
import com.shopify.api.service.voucher.VoucherService.OfferResult;
import com.shopify.api.service.voucher.VoucherService.RawLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VoucherService + VoucherStore + reconcile against a real (H2, PostgreSQL mode)
 * database and an in-memory Shopify. Not transactional, so concurrent issues
 * really commit and contend for the pool row.
 */
@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:vouchers;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "shopify.shop-url=test-shop.myshopify.com",
    "vouchers.feature-allowed=true",
    // Other entities use Postgres-only types (jsonb, arrays) H2 can't create; irrelevant here.
    "spring.jpa.show-sql=false",
    "logging.level.org.hibernate.tool.schema=OFF",
    "logging.level.org.hibernate.engine.jdbc.spi.SqlExceptionHelper=OFF"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({VoucherStore.class, VoucherSettingsService.class, VoucherOverrideService.class, VoucherService.class,
    VoucherReconcileService.class, VoucherReportService.class, VoucherScopeMonitor.class, VoucherClock.class,
    VoucherServiceIntegrationTest.Config.class})
class VoucherServiceIntegrationTest {

    @TestConfiguration
    static class Config {
        @Bean
        FakeVoucherGateway fakeVoucherGateway() {
            return new FakeVoucherGateway();
        }
    }

    // 2026-10-20 10:00 Melbourne (AEDT, UTC+11)
    static final Instant START = Instant.parse("2026-10-19T23:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    @Autowired VoucherService service;
    @Autowired VoucherReconcileService reconcile;
    @Autowired VoucherReportService report;
    @Autowired VoucherOverrideService overrides;
    @Autowired VoucherSettingsService settingsService;
    @Autowired VoucherScopeMonitor scope;
    @Autowired VoucherClock voucherClock;
    @Autowired VoucherStore store;
    @Autowired FakeVoucherGateway shopify;
    @Autowired VoucherSettingsRepository settingsRepo;
    @Autowired VoucherLedgerRepository ledgerRepo;
    @Autowired VoucherPoolDayRepository poolRepo;
    @Autowired VoucherClaimRepository claimRepo;
    @Autowired VoucherOverrideRepository overrideRepo;
    @Autowired VoucherOfferRepository offerRepo;

    MutableClock clock;

    @BeforeEach
    void setUp() {
        claimRepo.deleteAll();
        ledgerRepo.deleteAll();
        poolRepo.deleteAll();
        overrideRepo.deleteAll();
        offerRepo.deleteAll();
        settingsRepo.deleteAll();
        clock = new MutableClock(START);
        voucherClock.setClock(clock);
        shopify.ordersDenied = false;

        // Holdout 0 so tests are deterministic; holdout has its own tests.
        settingsRepo.save(VoucherSettings.defaults().toBuilder().enabled(true).holdoutPct(0).maxPerIpPerDay(20).build());
        settingsService.current();
        scope.set(java.util.Set.of("write_discounts"));
        shopify.scopes = java.util.Optional.of(java.util.Set.of("write_discounts"));
        shopify.codes.clear();
        shopify.orders.clear();
        shopify.deleted.clear();
        shopify.fetchCalls.set(0);
        shopify.createCalls.set(0);
        shopify.createMode = FakeVoucherGateway.CreateMode.OK;
        shopify.deleteFails = false;
        shopify.variants.clear();
        shopify.variant(1, "100.00", null, "Bandai", "Model Kit")
               .variant(2, "60.00", null, "Tamiya", "Paint")
               .variant(3, "80.00", "120.00", "Bandai", "Model Kit")   // on sale
               .variant(4, "200.00", null, "Games Workshop", "Miniatures")
               .variant(5, "30.00", null, "Tamiya", "Paint");
    }

    static OfferInput cart(BigDecimal amount, Object... idQty) {
        List<RawLine> lines = new ArrayList<>();
        for (int i = 0; i < idQty.length; i += 2) {
            lines.add(new RawLine(String.valueOf(idQty[i]), new BigDecimal(String.valueOf(idQty[i + 1]))));
        }
        return new OfferInput(lines, amount, "hesitating on price", null);
    }

    static VoucherChatContext chat(String session) {
        return new VoucherChatContext(session, null, "203.0.113.7");
    }

    static BigDecimal usd(String v) {
        return new BigDecimal(v);
    }

    BigDecimal reserved() {
        return store.reserved(TODAY).stripTrailingZeros();
    }

    // ----------------------------------------------------------------- issuing

    @Test
    void amountGuidanceFollowsTheTierSettings() {
        assertThat(service.amountGuidanceLabel())
            .isEqualTo("$5 or $10 for carts under $500; for carts of $500 or more, $15 to $25");

        settingsRepo.save(settingsService.current().toBuilder().highTierMaxAmount(usd("10")).build());
        settingsService.current();
        assertThat(service.amountGuidanceLabel()).isEqualTo("$5 or $10");
    }

    @Test
    void issuesAVoucherAndSetsEveryLimitOnTheShopifyCode() {
        OfferResult r = service.offer(cart(usd("10"), 1, 1, 2, 1), chat("s1"));

        assertThat(r.issued()).isTrue();
        String code = (String) r.fields().get("code");
        assertThat(code).startsWith("HH").hasSize(16);
        assertThat(r.fields().get("amount")).isEqualTo("10.00");
        assertThat((String) r.fields().get("cart_link"))
            .isEqualTo("https://test-shop.myshopify.com/cart/1:1,2:1?discount=" + code);

        FakeVoucherGateway.Live live = shopify.codes.get(code);
        assertThat(live.spec.amount()).isEqualByComparingTo("10");
        assertThat(live.spec.minSubtotal()).isEqualByComparingTo("150");
        assertThat(live.spec.endsAt()).isEqualTo(START.plus(Duration.ofMinutes(60)));
        assertThat(live.spec.variantGids()).containsExactly(FakeVoucherGateway.gid(1), FakeVoucherGateway.gid(2));
        assertThat(reserved()).isEqualByComparingTo("10");
        assertThat(ledgerRepo.findAll()).singleElement()
            .satisfies(e -> assertThat(e.getStatus()).isEqualTo(VoucherStatus.ISSUED));
    }

    @Test
    void injectedHugeAmountIsCappedAtTheOrderCeiling() {
        OfferResult r = service.offer(cart(usd("999"), 1, 1, 2, 1), chat("s1"));

        assertThat(r.issued()).isTrue();
        assertThat(r.fields().get("amount")).isEqualTo("10.00");
        assertThat((String) r.fields().get("instructions")).contains("I can't go higher than $10 on this order");
        assertThat(reserved()).isEqualByComparingTo("10");
    }

    @Test
    void tenPercentCeilingAppliesBelowMaxAmount() {
        // Raise maxAmount so the 10% rule is the binding cap: $160 cart -> floor($16 to $5) = $15
        settingsRepo.save(settingsService.current().toBuilder().maxAmount(usd("50")).dailyPool(usd("100")).build());
        OfferResult r = service.offer(cart(usd("50"), 1, 1, 2, 1), chat("s1"));

        assertThat(r.fields().get("amount")).isEqualTo("15.00");
        // and checkout enforces 10% too: $15 needs a $150 subtotal (max of 150 and 15/0.10)
        assertThat(shopify.codes.values().iterator().next().spec.minSubtotal()).isEqualByComparingTo("150");

        OfferResult big = service.offer(cart(usd("50"), 4, 2), chat("s2"));     // $400 -> $40
        String code = (String) big.fields().get("code");
        assertThat(big.fields().get("amount")).isEqualTo("40.00");
        assertThat(shopify.codes.get(code).spec.minSubtotal()).isEqualByComparingTo("400");
    }

    @Test
    void negativeZeroAndMissingAmountsAreRefused() {
        for (BigDecimal amount : new BigDecimal[]{usd("-10"), BigDecimal.ZERO, usd("4.99"), null}) {
            OfferResult r = service.offer(cart(amount, 1, 1, 2, 1), chat("neg-" + amount));
            assertThat(r.issued()).as("amount %s", amount).isFalse();
            assertThat(r.fields().get("reason_code")).isEqualTo("BAD_AMOUNT");
        }
        assertThat(shopify.createCalls.get()).isZero();
        assertThat(reserved()).isZero();
    }

    @Test
    void oddAmountsRoundDownToFiveDollarSteps() {
        OfferResult r = service.offer(cart(usd("9.99"), 1, 1, 2, 1), chat("s1"));
        assertThat(r.fields().get("amount")).isEqualTo("5.00");
    }

    @Test
    void subtotalComesFromShopifyNotTheModel() {
        // A $60 cart can't be talked up: there is no subtotal input, and fake quantities are refused.
        OfferResult cheap = service.offer(cart(usd("10"), 2, 1), chat("s1"));
        assertThat(cheap.fields().get("reason_code")).isEqualTo("BELOW_MINIMUM");
        assertThat(cheap.fields().get("amount_needed")).isEqualTo("90.00");

        OfferResult hugeQty = service.offer(cart(usd("10"), 2, 1000), chat("s2"));
        assertThat(hugeQty.fields().get("reason_code")).isEqualTo("BAD_CART");

        OfferResult fractional = service.offer(cart(usd("10"), 2, "2.5"), chat("s3"));
        assertThat(fractional.fields().get("reason_code")).isEqualTo("BAD_CART");

        OfferResult negativeQty = service.offer(cart(usd("10"), 4, -1), chat("s4"));
        assertThat(negativeQty.fields().get("reason_code")).isEqualTo("BAD_CART");

        OfferResult unknown = service.offer(cart(usd("10"), 999999, 1), chat("s5"));
        assertThat(unknown.fields().get("reason_code")).isEqualTo("BAD_CART");

        OfferResult junkId = service.offer(cart(usd("10"), "1 OR 1=1", 1), chat("s6"));
        assertThat(junkId.fields().get("reason_code")).isEqualTo("BAD_CART");

        assertThat(shopify.createCalls.get()).isZero();
    }

    @Test
    void saleItemsAndExclusionsDontCountAndArentDiscounted() {
        // $100 full price + $80 sale item: only $100 counts -> below minimum.
        OfferResult r = service.offer(cart(usd("10"), 1, 1, 3, 1), chat("s1"));
        assertThat(r.fields().get("reason_code")).isEqualTo("BELOW_MINIMUM");
        assertThat((String) r.fields().get("message")).contains("sale and excluded items don't count");

        settingsRepo.save(settingsService.current().toBuilder()
            .exclusions(new VoucherExclusions(List.of("games workshop"), List.of(), List.of()).toJson()).build());
        OfferResult excluded = service.offer(cart(usd("10"), 4, 1), chat("s2"));
        assertThat(excluded.fields().get("reason_code")).isEqualTo("BELOW_MINIMUM");

        // Eligible cart with a sale item in it: the code only applies to the full-price variants.
        OfferResult mixed = service.offer(cart(usd("10"), 1, 1, 2, 1, 3, 1), chat("s3"));
        assertThat(mixed.issued()).isTrue();
        assertThat(shopify.codes.get((String) mixed.fields().get("code")).spec.variantGids())
            .containsExactly(FakeVoucherGateway.gid(1), FakeVoucherGateway.gid(2));
    }

    // ----------------------------------------------------------------- limits

    @Test
    void oneVoucherPerSession() {
        assertThat(service.offer(cart(usd("10"), 1, 1, 2, 1), chat("s1")).issued()).isTrue();
        OfferResult again = service.offer(cart(usd("10"), 4, 1), chat("s1"));
        assertThat(again.fields().get("reason_code")).isEqualTo("ALREADY_ISSUED");
    }

    @Test
    void onePerCustomerPerDayAcrossSessions() {
        OfferInput withEmail = new OfferInput(cart(usd("10"), 1, 1, 2, 1).lines(), usd("10"), "r", "Jo@Example.com");
        assertThat(service.offer(withEmail, chat("s1")).issued()).isTrue();

        OfferInput sameEmail = new OfferInput(cart(usd("10"), 4, 1).lines(), usd("10"), "r", " jo@example.com ");
        assertThat(service.offer(sameEmail, chat("s2")).fields().get("reason_code")).isEqualTo("ALREADY_ISSUED");

        VoucherChatContext loggedIn = new VoucherChatContext("s3", "cust-42", "198.51.100.1");
        assertThat(service.offer(cart(usd("10"), 4, 1), loggedIn).issued()).isTrue();
        VoucherChatContext sameCustomer = new VoucherChatContext("s4", "cust-42", "198.51.100.2");
        assertThat(service.offer(cart(usd("10"), 4, 1), sameCustomer).issued()).isFalse();

        clock.advance(Duration.ofDays(1));
        assertThat(service.offer(sameEmail, chat("s5")).issued()).isTrue();
    }

    @Test
    void ipIsAWeakPerDayLimit() {
        settingsRepo.save(settingsService.current().toBuilder().maxPerIpPerDay(1).build());
        assertThat(service.offer(cart(usd("10"), 1, 1, 2, 1), chat("s1")).issued()).isTrue();
        assertThat(service.offer(cart(usd("10"), 4, 1), chat("s2")).issued()).isFalse();
        assertThat(service.offer(cart(usd("10"), 4, 1), new VoucherChatContext("s3", null, "192.0.2.9")).issued()).isTrue();
    }

    @Test
    void missingIpIsOneSharedLimitedBucket() {
        settingsRepo.save(settingsService.current().toBuilder().maxPerIpPerDay(1).build());
        assertThat(service.offer(cart(usd("10"), 4, 1), new VoucherChatContext("u1", null, null)).issued()).isTrue();
        assertThat(service.offer(cart(usd("10"), 4, 1), new VoucherChatContext("u2", null, "unknown")).issued()).isFalse();
        assertThat(service.offer(cart(usd("10"), 4, 1), new VoucherChatContext("u3", null, " ")).issued()).isFalse();
    }

    @Test
    void staleSettingsSaveFailsWithOptimisticLock() {
        VoucherSettings a = settingsService.currentStrict();
        VoucherSettings b = settingsService.currentStrict();
        settingsService.save(a.toBuilder().dailyPool(usd("20")).build());
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                settingsService.save(b.toBuilder().dailyPool(usd("200")).build()))
            .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        assertThat(settingsService.currentStrict().getDailyPool()).isEqualByComparingTo("20");
    }

    @Test
    void strictReadThrowsWhenTheRowIsMissing() {
        settingsRepo.deleteAll();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> settingsService.currentStrict())
            .isInstanceOf(VoucherSettingsService.SettingsUnavailableException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> settingsService.disable("test"))
            .isInstanceOf(VoucherSettingsService.SettingsUnavailableException.class);
        assertThat(settingsRepo.count()).isZero();             // nothing saved over it
    }

    @Test
    void poolCapsTheLastVoucherAndThenRunsDry() {
        for (int i = 0; i < 4; i++) {
            assertThat(service.offer(cart(usd("10"), 4, 1), chat("s" + i)).issued()).isTrue();
        }
        settingsRepo.save(settingsService.current().toBuilder().dailyPool(usd("45")).build());
        OfferResult last = service.offer(cart(usd("10"), 4, 1), chat("s4"));
        assertThat(last.fields().get("amount")).isEqualTo("5.00");
        OfferResult dry = service.offer(cart(usd("10"), 4, 1), chat("s5"));
        assertThat(dry.fields().get("reason_code")).isEqualTo("UNAVAILABLE");
        assertThat((String) dry.fields().get("message")).doesNotContainIgnoringCase("pool");
        assertThat(reserved()).isEqualByComparingTo("45");
    }

    @Test
    void poolNeverGoesNegativeUnderConcurrentIssues() throws Exception {
        int threads = 24;
        List<OfferResult> results = runConcurrently(threads, i -> service.offer(cart(usd("10"), 4, 1),
            new VoucherChatContext("c" + i, null, "10.0.0." + i)));

        long issued = results.stream().filter(OfferResult::issued).count();
        assertThat(issued).isEqualTo(5);                       // $50 / $10
        assertThat(reserved()).isEqualByComparingTo("50");
        BigDecimal ledgerTotal = ledgerRepo.findAll().stream()
            .filter(e -> e.getStatus() == VoucherStatus.ISSUED)
            .map(VoucherLedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(ledgerTotal).isEqualByComparingTo("50");
        assertThat(shopify.codes).hasSize(5);
    }

    @Test
    void concurrentCallsInOneSessionIssueOnce() throws Exception {
        List<OfferResult> results = runConcurrently(12, i -> service.offer(cart(usd("10"), 4, 1),
            new VoucherChatContext("same-session", null, "10.0.1." + i)));

        assertThat(results.stream().filter(OfferResult::issued).count()).isEqualTo(1);
        assertThat(reserved()).isEqualByComparingTo("10");
        assertThat(shopify.codes).hasSize(1);
    }

    interface Offer {
        OfferResult run(int i) throws Exception;
    }

    private List<OfferResult> runConcurrently(int n, Offer offer) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<OfferResult>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                final int idx = i;
                Callable<OfferResult> task = () -> {
                    go.await();
                    return offer.run(idx);
                };
                futures.add(pool.submit(task));
            }
            go.countDown();
            List<OfferResult> out = new ArrayList<>();
            for (Future<OfferResult> f : futures) out.add(f.get());
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    // ----------------------------------------------------------------- expiry / redemption

    @Test
    void unusedCodeIsDeletedAndReturnedToThePoolAfterGrace() {
        String code = (String) service.offer(cart(usd("10"), 4, 1), chat("s1")).fields().get("code");
        String discountId = shopify.codes.get(code).discountId;

        clock.advance(Duration.ofMinutes(62));               // expired, still inside the 5-min grace
        assertThat(reconcile.reconcile().expired()).isZero();
        assertThat(reserved()).isEqualByComparingTo("10");

        clock.advance(Duration.ofMinutes(4));                // 66 min after issue
        VoucherReconcileService.Summary s = reconcile.reconcile();
        assertThat(s.expired()).isEqualTo(1);
        assertThat(shopify.codes).doesNotContainKey(code);
        assertThat(shopify.deleted).containsExactly(discountId);
        assertThat(ledgerRepo.findAll()).singleElement()
            .satisfies(e -> assertThat(e.getStatus()).isEqualTo(VoucherStatus.EXPIRED));
        assertThat(reserved()).isZero();

        // The session's one voucher is still used up.
        assertThat(service.offer(cart(usd("10"), 4, 1), chat("s1")).issued()).isFalse();
        // A second pass changes nothing (value returned once).
        reconcile.reconcile();
        assertThat(reserved()).isZero();
    }

    @Test
    void valueReturnsToTheDayItWasReservedFrom() {
        clock.advance(Duration.ofHours(13).plusMinutes(30)); // 23:30 Melbourne
        service.offer(cart(usd("10"), 4, 1), chat("late"));
        clock.advance(Duration.ofMinutes(70));               // 00:40 next day
        reconcile.reconcile();
        assertThat(store.reserved(TODAY)).isEqualByComparingTo("0");
        assertThat(store.reserved(TODAY.plusDays(1))).isEqualByComparingTo("0");
        assertThat(poolRepo.findById(TODAY.plusDays(1))).isEmpty();
    }

    @Test
    void redeemedCodeStaysSpent() {
        String code = (String) service.offer(cart(usd("10"), 4, 1), chat("s1")).fields().get("code");
        shopify.redeem(code, "gid://shopify/Order/77", "190.00");

        clock.advance(Duration.ofMinutes(66));
        assertThat(reconcile.reconcile().redeemed()).isEqualTo(1);
        clock.advance(Duration.ofHours(3));
        reconcile.reconcile();

        VoucherLedgerEntry e = ledgerRepo.findAll().get(0);
        assertThat(e.getStatus()).isEqualTo(VoucherStatus.REDEEMED);
        assertThat(e.getOrderId()).isEqualTo("gid://shopify/Order/77");
        assertThat(shopify.deleted).isEmpty();
        assertThat(reserved()).isEqualByComparingTo("10");
    }

    @Test
    void withoutReadOrdersUnusedCodesStillExpireAndUsedOnesStillCount() {
        shopify.ordersDenied = true;
        String unused = (String) service.offer(cart(usd("10"), 4, 1), chat("s1")).fields().get("code");
        String used = (String) service.offer(cart(usd("10"), 4, 1), chat("s2")).fields().get("code");
        shopify.codes.get(used).usage = 1;

        clock.advance(Duration.ofMinutes(66));
        VoucherReconcileService.Summary s = reconcile.reconcile();

        assertThat(s.expired()).isEqualTo(1);
        assertThat(s.redeemed()).isEqualTo(1);
        assertThat(shopify.codes).doesNotContainKey(unused);
        assertThat(reserved()).isEqualByComparingTo("10");
    }

    @Test
    void usedButUnsearchableOrderStillCountsAsSpent() {
        String code = (String) service.offer(cart(usd("10"), 4, 1), chat("s1")).fields().get("code");
        shopify.codes.get(code).usage = 1;
        clock.advance(Duration.ofMinutes(66));
        reconcile.reconcile();
        assertThat(ledgerRepo.findAll().get(0).getStatus()).isEqualTo(VoucherStatus.REDEEMED);
        assertThat(reserved()).isEqualByComparingTo("10");
    }

    @Test
    void failedDeleteKeepsTheValueReservedUntilItSucceeds() {
        service.offer(cart(usd("10"), 4, 1), chat("s1"));
        shopify.deleteFails = true;
        clock.advance(Duration.ofMinutes(66));
        reconcile.reconcile();
        assertThat(ledgerRepo.findAll().get(0).getStatus()).isEqualTo(VoucherStatus.ISSUED);
        assertThat(reserved()).isEqualByComparingTo("10");

        shopify.deleteFails = false;
        reconcile.reconcile();
        assertThat(ledgerRepo.findAll().get(0).getStatus()).isEqualTo(VoucherStatus.EXPIRED);
        assertThat(reserved()).isZero();
    }

    // ----------------------------------------------------------------- Shopify failures

    @Test
    void definiteCreateFailureReturnsTheValueAndFreesTheSession() {
        shopify.createMode = FakeVoucherGateway.CreateMode.USER_ERROR;
        OfferResult r = service.offer(cart(usd("10"), 4, 1), chat("s1"));
        assertThat(r.fields().get("reason_code")).isEqualTo("CREATE_FAILED");
        assertThat(reserved()).isZero();
        assertThat(ledgerRepo.findAll().get(0).getStatus()).isEqualTo(VoucherStatus.FAILED);

        shopify.createMode = FakeVoucherGateway.CreateMode.OK;
        assertThat(service.offer(cart(usd("10"), 4, 1), chat("s1")).issued()).isTrue();
    }

    @Test
    void ambiguousCreateDeletesTheStrayCode() {
        shopify.createMode = FakeVoucherGateway.CreateMode.THROW_AFTER_CREATING;
        OfferResult r = service.offer(cart(usd("10"), 4, 1), chat("s1"));
        assertThat(r.issued()).isFalse();
        assertThat(shopify.codes).isEmpty();
        assertThat(shopify.deleted).hasSize(1);
        assertThat(reserved()).isZero();
    }

    @Test
    void stalePendingIsSettledByReconcile() {
        shopify.createMode = FakeVoucherGateway.CreateMode.THROW_AFTER_CREATING;
        shopify.deleteFails = true;                          // immediate cleanup fails too
        service.offer(cart(usd("10"), 4, 1), chat("s1"));
        assertThat(ledgerRepo.findAll().get(0).getStatus()).isEqualTo(VoucherStatus.PENDING);
        assertThat(reserved()).isEqualByComparingTo("10");

        shopify.deleteFails = false;
        clock.advance(Duration.ofMinutes(6));
        assertThat(reconcile.reconcile().failed()).isEqualTo(1);
        assertThat(shopify.codes).isEmpty();
        assertThat(reserved()).isZero();
    }

    @Test
    void missingWriteDiscountsScopeDisablesTheTool() {
        shopify.createMode = FakeVoucherGateway.CreateMode.ACCESS_DENIED;
        service.offer(cart(usd("10"), 4, 1), chat("s1"));
        assertThat(scope.ready()).isFalse();
        assertThat(service.toolAvailable()).isFalse();
        assertThat(service.offer(cart(usd("10"), 4, 1), chat("s2")).fields().get("reason_code")).isEqualTo("DISABLED");
        assertThat(reserved()).isZero();

        shopify.scopes = java.util.Optional.of(java.util.Set.of("read_products"));
        scope.refresh();
        assertThat(scope.ready()).isFalse();
        shopify.scopes = java.util.Optional.of(java.util.Set.of("write_discounts"));
        scope.refresh();
        assertThat(service.toolAvailable()).isTrue();
    }

    // ----------------------------------------------------------------- switches, holdout, overrides

    @Test
    void killSwitchBlocksEverythingWithoutCallingShopify() {
        settingsService.disable("test");
        assertThat(service.toolAvailable()).isFalse();
        OfferResult r = service.offer(cart(usd("10"), 4, 1), chat("s1"));
        assertThat(r.fields().get("reason_code")).isEqualTo("DISABLED");
        assertThat(shopify.fetchCalls.get()).isZero();
        assertThat(shopify.createCalls.get()).isZero();
        assertThat(ledgerRepo.count()).isZero();
    }

    @Test
    void missingSettingsRowMeansDisabled() {
        settingsRepo.deleteAll();
        assertThat(service.offer(cart(usd("10"), 4, 1), chat("s1")).fields().get("reason_code")).isEqualTo("DISABLED");
    }

    @Test
    void noChatContextMeansNoVoucher() {
        assertThat(service.offer(cart(usd("10"), 4, 1), null).fields().get("reason_code")).isEqualTo("NO_SESSION");
        assertThat(service.offer(cart(usd("10"), 4, 1), new VoucherChatContext(" ", null, null))
            .fields().get("reason_code")).isEqualTo("NO_SESSION");
    }

    @Test
    void holdoutSessionsAreNeverOfferedAndAreFlagged() {
        settingsRepo.save(settingsService.current().toBuilder().holdoutPct(100).build());
        OfferResult r = service.offer(cart(usd("10"), 4, 1), chat("h1"));
        assertThat(r.fields().get("reason_code")).isEqualTo("HOLDOUT");
        assertThat(shopify.createCalls.get()).isZero();
        assertThat(reserved()).isZero();
        VoucherOffer offer = offerRepo.findAll().get(0);
        assertThat(offer.isHoldout()).isTrue();
        assertThat(offer.isEligible()).isTrue();

        // Below-minimum carts aren't "eligible chats", so they're not counted as holdout.
        service.offer(cart(usd("10"), 2, 1), chat("h2"));
        assertThat(offerRepo.findAll()).filteredOn(VoucherOffer::isHoldout).hasSize(1);

        Map<String, Object> rep = report.report(TODAY, false);
        assertThat(rep.get("holdout_sessions")).isEqualTo(1);
        assertThat(rep.get("issued")).isEqualTo(0);
    }

    @Test
    void holdoutMatchesTheSessionHash() {
        settingsRepo.save(settingsService.current().toBuilder().holdoutPct(30).build());
        int held = 0;
        for (int i = 0; i < 40; i++) {
            String session = "sess-" + i;
            OfferResult r = service.offer(cart(usd("10"), 4, 1), new VoucherChatContext(session, null, null));
            boolean expected = VoucherRules.inHoldout(session, 30);
            assertThat("HOLDOUT".equals(r.fields().get("reason_code"))).as(session).isEqualTo(expected);
            if (expected) held++;
        }
        assertThat(held).isBetween(4, 22);
    }

    @Test
    void pauseOverrideBlocksAndExpires() {
        overrides.create(new VoucherOverrideService.Request(VoucherOverride.Type.PAUSE, null, null, 30, "stocktake"),
            "Nic", "127.0.0.1");
        assertThat(service.offer(cart(usd("10"), 4, 1), chat("s1")).fields().get("reason_code")).isEqualTo("PAUSED");
        clock.advance(Duration.ofMinutes(31));
        assertThat(service.offer(cart(usd("10"), 4, 1), chat("s1")).issued()).isTrue();
    }

    @Test
    void poolRaiseOverrideAddsToTodayOnly() {
        settingsRepo.save(settingsService.current().toBuilder().dailyPool(usd("10")).build());
        assertThat(service.offer(cart(usd("10"), 4, 1), chat("s1")).issued()).isTrue();
        assertThat(service.offer(cart(usd("10"), 4, 1), chat("s2")).issued()).isFalse();

        overrides.create(new VoucherOverrideService.Request(VoucherOverride.Type.POOL_RAISE, null, usd("20"), null, "busy day"),
            "Nic", "127.0.0.1");
        assertThat(service.offer(cart(usd("10"), 4, 1), chat("s3")).issued()).isTrue();
        assertThat(service.offer(cart(usd("10"), 4, 1), chat("s4")).issued()).isTrue();
        assertThat(service.offer(cart(usd("10"), 4, 1), chat("s5")).issued()).isFalse();

        clock.advance(Duration.ofDays(1));                  // raise ended at midnight
        assertThat(overrides.poolRaise(TODAY.plusDays(1))).isZero();
    }

    @Test
    void poolRaisesCannotBeStackedPastTheHardCeiling() {
        overrides.create(new VoucherOverrideService.Request(VoucherOverride.Type.POOL_RAISE, null, usd("400"), null, "a"),
            "Nic", "127.0.0.1");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> overrides.create(
                new VoucherOverrideService.Request(VoucherOverride.Type.POOL_RAISE, null, usd("200"), null, "b"),
                "Nic", "127.0.0.1"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(overrides.poolRaise(TODAY)).isEqualByComparingTo("400");
    }

    @Test
    void sessionOverrideAllowsMoreForThatSessionOnlyAndExpires() {
        overrides.create(new VoucherOverrideService.Request(VoucherOverride.Type.SESSION_AMOUNT, "vip", usd("25"), 60, "loyal"),
            "Nic", "127.0.0.1");
        assertThat(service.offer(cart(usd("25"), 4, 1), chat("vip")).fields().get("amount")).isEqualTo("25.00");
        assertThat(service.offer(cart(usd("25"), 4, 1), chat("other")).fields().get("amount")).isEqualTo("10.00");

        overrides.create(new VoucherOverrideService.Request(VoucherOverride.Type.SESSION_AMOUNT, "vip2", usd("25"), 60, "loyal"),
            "Nic", "127.0.0.1");
        clock.advance(Duration.ofMinutes(61));
        assertThat(service.offer(cart(usd("25"), 4, 1), chat("vip2")).fields().get("amount")).isEqualTo("10.00");
    }

    // ----------------------------------------------------------------- tiers, sale items, overrides

    @Test
    void tierCapIsTenDollarsBelowFiveHundredAndTwentyFiveFrom() {
        settingsRepo.save(settingsService.current().toBuilder().dailyPool(usd("200")).build());
        // $460 full-price cart: 10% = $45 after rounding, but the base tier caps at $10.
        OfferResult small = service.offer(cart(usd("50"), 4, 2, 2, 1), chat("t1"));
        assertThat(small.fields().get("amount")).isEqualTo("10.00");

        // $500 exactly: high tier, $25 (10% = $50 still above it).
        OfferResult big = service.offer(cart(usd("50"), 4, 2, 1, 1), chat("t2"));
        String code = (String) big.fields().get("code");
        assertThat(big.fields().get("amount")).isEqualTo("25.00");
        // checkout enforces the tier: a $25 code needs a $500 cart, not just $250 (25 / 10%).
        assertThat(shopify.codes.get(code).spec.minSubtotal()).isEqualByComparingTo("500");

        // A $10 code on a big cart keeps the normal minimum.
        OfferResult modest = service.offer(cart(usd("10"), 4, 3), chat("t3"));
        assertThat(shopify.codes.get((String) modest.fields().get("code")).spec.minSubtotal()).isEqualByComparingTo("150");
    }

    @Test
    void tenPercentStillBindsInsideTheHighTier() {
        settingsRepo.save(settingsService.current().toBuilder().dailyPool(usd("200"))
            .highTierSubtotal(usd("200")).highTierMaxAmount(usd("50")).build());
        // $230 cart in the (lowered) high tier: 10% = $23 -> $20.
        OfferResult r = service.offer(cart(usd("50"), 4, 1, 5, 1), chat("t1"));
        assertThat(r.fields().get("amount")).isEqualTo("20.00");
    }

    @Test
    void saleItemsAreListedInTheOffer() {
        // $260 full price + an $80 item on sale (was $120).
        OfferResult r = service.offer(cart(usd("10"), 4, 1, 2, 1, 3, 2), chat("s1"));
        assertThat(r.issued()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sale = (List<Map<String, Object>>) r.fields().get("already_discounted_items");
        assertThat(sale).singleElement().satisfies(m -> {
            assertThat(m.get("variant_id")).isEqualTo("3");
            assertThat(m.get("title")).isEqualTo("Product 3");
            assertThat(m.get("quantity")).isEqualTo(2);
            assertThat(m.get("price")).isEqualTo("80.00");
            assertThat(m.get("compare_at_price")).isEqualTo("120.00");
        });
        assertThat((String) r.fields().get("instructions")).contains("already on sale").contains("rest of the order");

        OfferResult plain = service.offer(cart(usd("10"), 4, 1), chat("s2"));
        assertThat(plain.fields()).doesNotContainKey("already_discounted_items");
    }

    @Test
    void codesAreNotLockedToACustomer() {
        OfferInput withEmail = new OfferInput(cart(usd("10"), 4, 1).lines(), usd("10"), "r", "jo@example.com");
        OfferResult r = service.offer(withEmail, chat("s1"));
        assertThat(r.issued()).isTrue();
        assertThat(r.fields()).doesNotContainKey("locked_to_email");
    }

    @Test
    void emailAliasesCountAsOneCustomerPerDay() {
        assertThat(service.offer(new OfferInput(cart(usd("10"), 4, 1).lines(), usd("10"), "r", "jo.smith@gmail.com"),
            chat("s1")).issued()).isTrue();
        for (String alias : new String[]{"josmith@gmail.com", "Jo.Smith+deal@gmail.com", "j.o.s.m.i.t.h@googlemail.com"}) {
            OfferResult r = service.offer(new OfferInput(cart(usd("10"), 4, 1).lines(), usd("10"), "r", alias),
                new VoucherChatContext("s-" + alias, null, null));
            assertThat(r.fields().get("reason_code")).as(alias).isEqualTo("ALREADY_ISSUED");
        }
    }

    @Test
    void ownerOverrideIsCappedAtTwentyPercentOfTheCart() {
        overrides.create(new VoucherOverrideService.Request(VoucherOverride.Type.SESSION_AMOUNT, "vip", usd("100"), 60, "big order"),
            "Nic", "127.0.0.1");
        settingsRepo.save(settingsService.current().toBuilder().dailyPool(usd("200")).build());
        // $200 cart: 20% = $40, even though the override allows $100.
        OfferResult r = service.offer(cart(usd("100"), 4, 1), chat("vip"));
        String code = (String) r.fields().get("code");
        assertThat(r.fields().get("amount")).isEqualTo("40.00");
        assertThat(shopify.codes.get(code).spec.minSubtotal()).isEqualByComparingTo("200");   // 40 / 20%
    }

    @Test
    void reportCountsTheDay() {
        String used = (String) service.offer(cart(usd("10"), 4, 1), chat("a")).fields().get("code");
        service.offer(cart(usd("10"), 4, 1), chat("b"));
        service.offer(cart(usd("10"), 2, 1), chat("c"));    // below minimum: not "offered"
        shopify.redeem(used, "gid://shopify/Order/5", "210.00");
        clock.advance(Duration.ofMinutes(66));
        reconcile.reconcile();

        Map<String, Object> r = report.report(TODAY, true);
        assertThat(r.get("offered_sessions")).isEqualTo(2);
        assertThat(r.get("issued")).isEqualTo(2);
        assertThat(r.get("redeemed")).isEqualTo(1);
        assertThat(r.get("expired")).isEqualTo(1);
        assertThat(r.get("dollars_spent")).isEqualTo("10.00");
        assertThat(r.get("pool_remaining")).isEqualTo("40.00");
        assertThat(r.get("aov_with_voucher")).isEqualTo("210.00");
        assertThat(r.get("aov_without_voucher")).isEqualTo("110.00");
    }
}
