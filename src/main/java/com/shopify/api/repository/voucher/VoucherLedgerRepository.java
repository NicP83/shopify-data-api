package com.shopify.api.repository.voucher;

import com.shopify.api.model.voucher.VoucherLedgerEntry;
import com.shopify.api.model.voucher.VoucherStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface VoucherLedgerRepository extends JpaRepository<VoucherLedgerEntry, Long> {

    /**
     * Compare-and-set the status. Returns 1 only for the caller that moved the row
     * out of {@code from}, so a value is returned to the pool at most once even if
     * two reconcile passes overlap.
     */
    @Modifying
    @Query("update VoucherLedgerEntry e set e.status = :to, e.resolvedAt = :now "
         + "where e.id = :id and e.status = :from")
    int transition(@Param("id") Long id, @Param("from") VoucherStatus from,
                   @Param("to") VoucherStatus to, @Param("now") Instant now);

    @Modifying
    @Query("update VoucherLedgerEntry e set e.status = com.shopify.api.model.voucher.VoucherStatus.ISSUED, "
         + "e.shopifyDiscountId = :discountId, e.shopifyCustomerId = :customerId where e.id = :id "
         + "and e.status = com.shopify.api.model.voucher.VoucherStatus.PENDING")
    int markIssued(@Param("id") Long id, @Param("discountId") String discountId,
                   @Param("customerId") String customerId);

    @Modifying
    @Query("update VoucherLedgerEntry e set e.status = com.shopify.api.model.voucher.VoucherStatus.REDEEMED, "
         + "e.orderId = :orderId, e.orderTotal = :orderTotal, e.resolvedAt = :now where e.id = :id "
         + "and e.status = com.shopify.api.model.voucher.VoucherStatus.ISSUED")
    int markRedeemed(@Param("id") Long id, @Param("orderId") String orderId,
                     @Param("orderTotal") java.math.BigDecimal orderTotal, @Param("now") Instant now);

    @Modifying
    @Query("update VoucherLedgerEntry e set e.reconcileAttempts = e.reconcileAttempts + 1, "
         + "e.lastError = :error where e.id = :id")
    int recordAttempt(@Param("id") Long id, @Param("error") String error);

    List<VoucherLedgerEntry> findByStatusAndExpiresAtBefore(VoucherStatus status, Instant cutoff);

    List<VoucherLedgerEntry> findByStatusAndIssuedAtBefore(VoucherStatus status, Instant cutoff);

    List<VoucherLedgerEntry> findByPoolDateOrderByIssuedAtDesc(LocalDate poolDate);

    @Query("select count(e) from VoucherLedgerEntry e where e.ipHash = :ip and e.poolDate = :d "
         + "and e.status <> com.shopify.api.model.voucher.VoucherStatus.FAILED")
    long countLiveForIp(@Param("ip") String ipHash, @Param("d") LocalDate day);
}
