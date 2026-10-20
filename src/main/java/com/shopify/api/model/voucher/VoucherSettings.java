package com.shopify.api.model.voucher;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    /** Largest single voucher without an owner override. */
    @Column(name = "max_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "min_subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal minSubtotal;

    @Column(name = "validity_minutes", nullable = false)
    private int validityMinutes;

    @Column(name = "holdout_pct", nullable = false)
    private int holdoutPct;

    @Column(name = "max_per_ip_per_day", nullable = false)
    private int maxPerIpPerDay;

    /** Ask for the checkout email and lock the Shopify code to that customer. */
    @Column(name = "require_email", nullable = false)
    private boolean requireEmail;

    /** JSON {"vendors":[],"productTypes":[],"tags":[]}; parsed by VoucherExclusions. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String exclusions;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    /** The defaults V024 seeds: disabled, AUD 50 pool, 10%, $10 max, $150 min, 60 min, 30% holdout, email required. */
    public static VoucherSettings defaults() {
        return VoucherSettings.builder()
                .id(SINGLETON_ID)
                .enabled(false)
                .dailyPool(new BigDecimal("50.00"))
                .maxPct(new BigDecimal("0.1000"))
                .maxAmount(new BigDecimal("10.00"))
                .minSubtotal(new BigDecimal("150.00"))
                .validityMinutes(60)
                .holdoutPct(30)
                .maxPerIpPerDay(1)
                .requireEmail(true)
                .exclusions("{\"vendors\":[],\"productTypes\":[],\"tags\":[]}")
                .updatedAt(Instant.now())
                .build();
    }
}
