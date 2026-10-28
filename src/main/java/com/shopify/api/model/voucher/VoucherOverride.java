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

/** An owner override: temporary (expires_at) and audited (never deleted, only revoked). */
@Entity
@Table(name = "voucher_overrides")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherOverride {

    public enum Type { POOL_RAISE, SESSION_AMOUNT, PAUSE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    /** SESSION_AMOUNT: the chat session id. */
    private String target;

    @Column(name = "override_value", precision = 10, scale = 2)
    private BigDecimal value;

    /** POOL_RAISE: the Melbourne day it raises. */
    @Column(name = "pool_date")
    private LocalDate poolDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "source_ip", length = 64)
    private String sourceIp;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private String revokedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
