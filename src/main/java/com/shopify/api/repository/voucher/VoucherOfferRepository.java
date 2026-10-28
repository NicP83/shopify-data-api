package com.shopify.api.repository.voucher;

import com.shopify.api.model.voucher.VoucherOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface VoucherOfferRepository extends JpaRepository<VoucherOffer, Long> {

    List<VoucherOffer> findByPoolDate(LocalDate poolDate);
}
