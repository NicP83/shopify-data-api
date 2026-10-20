package com.shopify.api.model.voucher;

/**
 * Ledger status. PENDING = value reserved while the Shopify create is in flight;
 * EXPIRED, REVOKED and FAILED have returned their value to the pool.
 */
public enum VoucherStatus {
    PENDING, ISSUED, REDEEMED, EXPIRED, REVOKED, FAILED
}
