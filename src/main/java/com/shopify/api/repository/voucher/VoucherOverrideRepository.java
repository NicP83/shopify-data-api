package com.shopify.api.repository.voucher;

import com.shopify.api.model.voucher.VoucherOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface VoucherOverrideRepository extends JpaRepository<VoucherOverride, Long> {

    @Query("select o from VoucherOverride o where o.type = :type and o.revokedAt is null and o.expiresAt > :now")
    List<VoucherOverride> findActive(@Param("type") VoucherOverride.Type type, @Param("now") Instant now);

    List<VoucherOverride> findTop50ByOrderByCreatedAtDesc();
}
