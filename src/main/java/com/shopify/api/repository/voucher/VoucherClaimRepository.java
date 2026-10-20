package com.shopify.api.repository.voucher;

import com.shopify.api.model.voucher.VoucherClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VoucherClaimRepository extends JpaRepository<VoucherClaim, String> {

    @Modifying
    @Query("delete from VoucherClaim c where c.ledgerId = :ledgerId")
    int deleteByLedgerId(@Param("ledgerId") Long ledgerId);
}
