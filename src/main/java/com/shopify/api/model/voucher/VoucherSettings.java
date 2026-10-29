package com.shopify.api.model.voucher;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** The single voucher settings row (id 1). See V024 and docs/vouchers/VOUCHERS.md. */
@Entity
@Table(name = "voucher_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class VoucherSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "daily_pool", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyPool;

    /** Fraction of the eligible subtotal, e.g. 0.10. */
    @Column(name = "max_pct", nullable = false, precision = 5, scale = 4)
    private BigDecimal maxPct;

    /** Largest single voucher (no owner override) for carts below highTierSubtotal. */
    @Column(name = "max_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxAmount;

    /** Eligible subtotal from which the high-tier cap applies. */
    @Column(name = "high_tier_subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal highTierSubtotal;

    /** Largest single voucher (no owner override) for carts at or above highTierSubtotal. */
    @Column(name = "high_tier_max_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal highTierMaxAmount;

    @Column(name = "min_subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal minSubtotal;

    @Column(name = "validity_minutes", nullable = false)
    private int validityMinutes;

    @Column(name = "holdout_pct", nullable = false)
    private int holdoutPct;

    @Column(name = "max_per_ip_per_day", nullable = false)
    private int maxPerIpPerDay;

    /** JSON {"vendors":[],"productTypes":[],"tags":[]}; parsed by VoucherExclusions. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String exclusions;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    /** Optimistic lock (V025): saving from a stale read fails instead of overwriting. */
    @Version
    @Column(nullable = false)
    private Long version;

    /** The defaults V024 seeds: disabled, AUD 50 pool, 10%, $10 max ($25 from a $500 cart), $150 min, 60 min, 30% holdout. */
    public static VoucherSettings defaults() {
        return VoucherSettings.builder()
                .id(SINGLETON_ID)
                .enabled(false)
                .dailyPool(new BigDecimal("50.00"))
                .maxPct(new BigDecimal("0.1000"))
                .maxAmount(new BigDecimal("10.00"))
                .highTierSubtotal(new BigDecimal("500.00"))
                .highTierMaxAmount(new BigDecimal("25.00"))
                .minSubtotal(new BigDecimal("150.00"))
                .validityMinutes(60)
                .holdoutPct(30)
                .maxPerIpPerDay(1)
                .exclusions("{\"vendors\":[],\"productTypes\":[],\"tags\":[]}")
                .updatedAt(Instant.now())
                .build();
    }
}
