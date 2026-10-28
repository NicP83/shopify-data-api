package com.shopify.api.model.voucher;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Value reserved from one Melbourne day's pool (ISSUED + REDEEMED codes). */
@Entity
@Table(name = "voucher_pool_days")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoucherPoolDay {

    @Id
    @Column(name = "pool_date")
    private LocalDate poolDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal reserved;
}
