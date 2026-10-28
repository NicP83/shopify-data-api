package com.shopify.api.repository.voucher;

import com.shopify.api.model.voucher.VoucherPoolDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Repository
public interface VoucherPoolDayRepository extends JpaRepository<VoucherPoolDay, LocalDate> {

    /** Create the day's row if missing (no-op when it exists). */
    @Modifying
    @Query(value = "INSERT INTO voucher_pool_days (pool_date, reserved) VALUES (:d, 0) ON CONFLICT DO NOTHING",
           nativeQuery = true)
    int ensureDay(@Param("d") LocalDate day);

    /**
     * Atomically reserve {@code amount}; succeeds (returns 1) only while the
     * day's total stays within {@code limit}. Concurrent callers serialise on the
     * row lock and the WHERE is re-checked, so the pool can never be overdrawn.
     */
    @Modifying
    @Query("update VoucherPoolDay p set p.reserved = p.reserved + :amount "
         + "where p.poolDate = :d and p.reserved + :amount <= :limit")
    int reserve(@Param("d") LocalDate day, @Param("amount") BigDecimal amount, @Param("limit") BigDecimal limit);

    /** Return {@code amount} to the day's pool (never below zero). */
    @Modifying
    @Query("update VoucherPoolDay p set p.reserved = p.reserved - :amount "
         + "where p.poolDate = :d and p.reserved >= :amount")
    int release(@Param("d") LocalDate day, @Param("amount") BigDecimal amount);
}
