package com.shopify.api.model.voucher;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "voucher_ledger")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "pool_date", nullable = false)
    private LocalDate poolDate;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    /** SHA-256 of the strongest customer identifier known (email or storefront id), or null. */
    @Column(name = "customer_key", length = 128)
    private String customerKey;

    @Column(name = "ip_hash", length = 128)
    private String ipHash;

    /** Eligible subtotal recomputed from Shopify prices (never the model's number). */
    @Column(name = "cart_subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal cartSubtotal;

    /** Minimum subtotal set on the Shopify code. */
    @Column(name = "min_subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal minSubtotal;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoucherStatus status;

    @Column(name = "shopify_discount_id")
    private String shopifyDiscountId;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "order_total", precision = 10, scale = 2)
    private BigDecimal orderTotal;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "reconcile_attempts", nullable = false)
    private int reconcileAttempts;

    @Column(name = "last_error", length = 500)
    private String lastError;
}
