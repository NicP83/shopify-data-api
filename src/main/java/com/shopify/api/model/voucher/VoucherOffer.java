package com.shopify.api.model.voucher;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** One offer_discount call and its outcome, for the daily report. */
@Entity
@Table(name = "voucher_offers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "pool_date", nullable = false)
    private LocalDate poolDate;

    @Column(name = "cart_subtotal", precision = 10, scale = 2)
    private BigDecimal cartSubtotal;

    @Column(precision = 10, scale = 2)
    private BigDecimal requested;

    @Column(nullable = false, length = 30)
    private String outcome;

    @Column(nullable = false)
    private boolean eligible;

    @Column(nullable = false)
    private boolean holdout;

    @Column(name = "ledger_id")
    private Long ledgerId;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
