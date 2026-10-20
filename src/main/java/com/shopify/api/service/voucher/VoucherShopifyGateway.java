package com.shopify.api.service.voucher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The Shopify calls vouchers need, behind an interface so tests never touch Shopify.
 * Methods throw {@link GatewayException} when the outcome is unknown (timeout,
 * transport error); callers must then assume nothing happened OR everything did.
 */
public interface VoucherShopifyGateway {

    record VariantInfo(String variantGid, String productGid, BigDecimal price, BigDecimal compareAtPrice,
                       String vendor, String productType, List<String> tags,
                       boolean availableForSale, String productStatus) {

        public boolean onSale() {
            return compareAtPrice != null && price != null && compareAtPrice.compareTo(price) > 0;
        }
    }

    record CodeSpec(String code, String title, BigDecimal amount, BigDecimal minSubtotal,
                    List<String> variantGids, Instant startsAt, Instant endsAt) {}

    /** ok: created. accessDenied: the token lacks write_discounts. */
    record CreateResult(boolean ok, String discountId, boolean accessDenied, String error) {}

    record CodeState(boolean found, String discountId, int usageCount) {}

    record OrderInfo(String orderId, BigDecimal total) {}

    class GatewayException extends RuntimeException {
        public GatewayException(String message) {
            super(message);
        }
    }

    /** Variants that exist; unknown ids are simply absent from the result. */
    List<VariantInfo> fetchVariants(List<String> variantGids);

    CreateResult createCode(CodeSpec spec);

    CodeState lookupCode(String code);

    Optional<OrderInfo> findOrderByCode(String code);

    /** true when Shopify confirms the code discount is gone. */
    boolean deleteCode(String discountId);

    /** true / false when known; empty when the check itself failed. */
    Optional<Boolean> hasWriteDiscountsScope();

    /** Order totals created in [from, to), best-effort, capped at one page. */
    List<OrderInfo> ordersBetween(Instant from, Instant to);
}
