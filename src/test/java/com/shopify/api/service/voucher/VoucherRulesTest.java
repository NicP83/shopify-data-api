package com.shopify.api.service.voucher;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VoucherRulesTest {

    @Test
    void floorsToFiveDollarSteps() {
        assertThat(VoucherRules.floorToStep(new BigDecimal("14.99"))).isEqualByComparingTo("10");
        assertThat(VoucherRules.floorToStep(new BigDecimal("15"))).isEqualByComparingTo("15");
        assertThat(VoucherRules.floorToStep(new BigDecimal("4.99"))).isEqualByComparingTo("0");
        assertThat(VoucherRules.floorToStep(new BigDecimal("-20"))).isEqualByComparingTo("0");
        assertThat(VoucherRules.floorToStep(null)).isEqualByComparingTo("0");
    }

    @Test
    void tenPercentCeiling() {
        BigDecimal pct = new BigDecimal("0.10");
        assertThat(VoucherRules.pctCeiling(new BigDecimal("150"), pct)).isEqualByComparingTo("15");
        assertThat(VoucherRules.pctCeiling(new BigDecimal("149.99"), pct)).isEqualByComparingTo("10");
        assertThat(VoucherRules.pctCeiling(new BigDecimal("99"), pct)).isEqualByComparingTo("5");
    }

    @Test
    void codeMinimumMakesCheckoutEnforceThePercentage() {
        BigDecimal pct = new BigDecimal("0.10");
        BigDecimal min = new BigDecimal("150");
        assertThat(VoucherRules.codeMinSubtotal(new BigDecimal("10"), pct, min)).isEqualByComparingTo("150");
        assertThat(VoucherRules.codeMinSubtotal(new BigDecimal("20"), pct, min)).isEqualByComparingTo("200");
        assertThat(VoucherRules.codeMinSubtotal(new BigDecimal("25"), new BigDecimal("0.12"), min)).isEqualByComparingTo("209");
    }

    @Test
    void codesAreLongRandomAndUnambiguous() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            String c = VoucherRules.generateCode();
            assertThat(c).hasSize(16).matches("HH[2-9A-HJKMNP-Z]{14}");
            assertThat(seen.add(c)).isTrue();
        }
    }

    @Test
    void holdoutIsDeterministicAndRoughlyTheConfiguredShare() {
        int held = 0;
        for (int i = 0; i < 10_000; i++) {
            String s = "session-" + i;
            boolean h = VoucherRules.inHoldout(s, 30);
            assertThat(VoucherRules.inHoldout(s, 30)).isEqualTo(h);
            if (h) held++;
        }
        assertThat(held).isBetween(2700, 3300);
        assertThat(VoucherRules.inHoldout("x", 0)).isFalse();
        assertThat(VoucherRules.inHoldout("x", 100)).isTrue();
    }

    @Test
    void dollarsLabel() {
        assertThat(VoucherRules.dollars(new BigDecimal("10.00"))).isEqualTo("$10");
        assertThat(VoucherRules.dollars(new BigDecimal("12.5"))).isEqualTo("$12.50");
    }
}
