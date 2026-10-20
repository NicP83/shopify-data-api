package com.shopify.api.service.voucher;

import com.shopify.api.model.voucher.VoucherSettings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoucherSettingsServiceTest {

    private final VoucherSettings base = VoucherSettings.defaults();

    @Test
    void defaultsAreDisabledAndValid() {
        assertThat(base.isEnabled()).isFalse();
        VoucherSettingsService.validate(base);
    }

    @Test
    void hardCeilingsCannotBeExceededEvenByTheOwner() {
        assertThatThrownBy(() -> VoucherSettingsService.validate(base.toBuilder().dailyPool(new BigDecimal("501")).build()))
            .hasMessageContaining("dailyPool");
        assertThatThrownBy(() -> VoucherSettingsService.validate(base.toBuilder().maxPct(new BigDecimal("0.30")).build()))
            .hasMessageContaining("maxPct");
        assertThatThrownBy(() -> VoucherSettingsService.validate(base.toBuilder().maxAmount(new BigDecimal("12")).build()))
            .hasMessageContaining("maxAmount");
        assertThatThrownBy(() -> VoucherSettingsService.validate(base.toBuilder().minSubtotal(new BigDecimal("10")).build()))
            .hasMessageContaining("minSubtotal");
        assertThatThrownBy(() -> VoucherSettingsService.validate(base.toBuilder().validityMinutes(600).build()))
            .hasMessageContaining("validityMinutes");
        assertThatThrownBy(() -> VoucherSettingsService.validate(base.toBuilder().dailyPool(new BigDecimal("-1")).build()))
            .hasMessageContaining("dailyPool");
    }

    @Test
    void looseningIsDetectedPerField() {
        VoucherSettings looser = base.toBuilder().enabled(true).dailyPool(new BigDecimal("80"))
            .maxPct(new BigDecimal("0.12")).maxAmount(new BigDecimal("15")).minSubtotal(new BigDecimal("120"))
            .validityMinutes(90).holdoutPct(10).maxPerIpPerDay(2).build();
        assertThat(VoucherSettingsService.loosenings(base, looser)).containsExactly("enabled", "dailyPool", "maxPct",
            "maxAmount", "minSubtotal", "validityMinutes", "holdoutPct", "maxPerIpPerDay");

        VoucherSettings withExclusion = base.toBuilder()
            .exclusions(new VoucherExclusions(List.of("Bandai"), List.of(), List.of()).toJson()).build();
        assertThat(VoucherSettingsService.loosenings(base, withExclusion)).isEmpty();
        assertThat(VoucherSettingsService.loosenings(withExclusion, base)).containsExactly("exclusions (removed entries)");
    }

    @Test
    void perTurnCacheNeverBlocksOnTheDbAndStartsDisabled() throws Exception {
        com.shopify.api.repository.voucher.VoucherSettingsRepository repo =
            org.mockito.Mockito.mock(com.shopify.api.repository.voucher.VoucherSettingsRepository.class);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.when(repo.findById(VoucherSettings.SINGLETON_ID)).thenAnswer(i -> {
            release.await();                                   // a hung DB
            return java.util.Optional.of(base.toBuilder().enabled(true).build());
        });
        VoucherSettingsService service = new VoucherSettingsService(repo);

        long start = System.nanoTime();
        assertThat(service.cached().isEnabled()).isFalse();   // fail closed until loaded
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(500);

        release.countDown();
        for (int i = 0; i < 50 && !service.cached().isEnabled(); i++) Thread.sleep(20);
        assertThat(service.cached().isEnabled()).isTrue();
    }

    @Test
    void tighteningAndDisablingAreNotLoosening() {
        VoucherSettings on = base.toBuilder().enabled(true).build();
        VoucherSettings tighter = on.toBuilder().enabled(false).dailyPool(new BigDecimal("20"))
            .maxAmount(new BigDecimal("5")).minSubtotal(new BigDecimal("200")).validityMinutes(30).holdoutPct(50).build();
        assertThat(VoucherSettingsService.loosenings(on, tighter)).isEmpty();
    }
}
