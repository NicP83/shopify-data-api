package com.shopify.api.repository.voucher;

import com.shopify.api.model.voucher.VoucherSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VoucherSettingsRepository extends JpaRepository<VoucherSettings, Long> {
}
