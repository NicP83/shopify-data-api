package com.shopify.api.model.voucher;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A uniqueness claim ("s:<session>" or "c:<day>:<customer>"); the primary key enforces the limit. */
@Entity
@Table(name = "voucher_claims")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoucherClaim {

    @Id
    @Column(name = "claim_key")
    private String claimKey;

    @Column(name = "ledger_id", nullable = false)
    private Long ledgerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
