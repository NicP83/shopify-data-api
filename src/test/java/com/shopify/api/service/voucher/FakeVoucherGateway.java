package com.shopify.api.service.voucher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory Shopify for voucher tests. No network. */
class FakeVoucherGateway implements VoucherShopifyGateway {

    enum CreateMode { OK, USER_ERROR, ACCESS_DENIED, THROW_BEFORE, THROW_AFTER_CREATING }

    static final class Live {
        final CodeSpec spec;
        final String discountId;
        volatile int usage;

        Live(CodeSpec spec, String discountId) {
            this.spec = spec;
            this.discountId = discountId;
        }
    }

    final Map<String, VariantInfo> variants = new ConcurrentHashMap<>();
    final Map<String, Live> codes = new ConcurrentHashMap<>();
    final Map<String, OrderInfo> orders = new ConcurrentHashMap<>();
    final List<String> deleted = new java.util.concurrent.CopyOnWriteArrayList<>();
    final AtomicInteger fetchCalls = new AtomicInteger();
    final AtomicInteger createCalls = new AtomicInteger();
    final AtomicInteger ids = new AtomicInteger();
    volatile CreateMode createMode = CreateMode.OK;
    volatile boolean deleteFails;
    volatile Optional<java.util.Set<String>> scopes = Optional.of(java.util.Set.of("write_discounts"));

    static String gid(long id) {
        return VoucherService.VARIANT_GID_PREFIX + id;
    }

    FakeVoucherGateway variant(long id, String price, String compareAt, String vendor, String type, String... tags) {
        variants.put(gid(id), new VariantInfo(gid(id), "gid://shopify/Product/" + id, "Product " + id, new BigDecimal(price),
            compareAt == null ? null : new BigDecimal(compareAt), vendor, type, List.of(tags), true, "ACTIVE"));
        return this;
    }

    @Override
    public List<VariantInfo> fetchVariants(List<String> variantGids) {
        fetchCalls.incrementAndGet();
        List<VariantInfo> out = new ArrayList<>();
        for (String g : variantGids) {
            VariantInfo v = variants.get(g);
            if (v != null) out.add(v);
        }
        return out;
    }

    @Override
    public CreateResult createCode(CodeSpec spec) {
        createCalls.incrementAndGet();
        switch (createMode) {
            case USER_ERROR -> { return new CreateResult(false, null, false, "Code must be unique"); }
            case ACCESS_DENIED -> { return new CreateResult(false, null, true, "Access denied"); }
            case THROW_BEFORE -> throw new GatewayException("timeout");
            case THROW_AFTER_CREATING -> {
                codes.put(spec.code(), new Live(spec, "gid://shopify/DiscountCodeNode/" + ids.incrementAndGet()));
                throw new GatewayException("read timeout");
            }
            default -> { }
        }
        Live live = new Live(spec, "gid://shopify/DiscountCodeNode/" + ids.incrementAndGet());
        codes.put(spec.code(), live);
        return new CreateResult(true, live.discountId, false, null);
    }

    @Override
    public CodeState lookupCode(String code) {
        Live live = codes.get(code);
        return live == null ? new CodeState(false, null, 0) : new CodeState(true, live.discountId, live.usage);
    }

    @Override
    public Optional<OrderInfo> findOrderByCode(String code) {
        return Optional.ofNullable(orders.get(code));
    }

    @Override
    public boolean deleteCode(String discountId) {
        if (deleteFails) return false;
        codes.entrySet().removeIf(e -> e.getValue().discountId.equals(discountId));
        deleted.add(discountId);
        return true;
    }

    @Override
    public Optional<java.util.Set<String>> grantedScopes() {
        return scopes;
    }

    @Override
    public List<OrderInfo> ordersBetween(Instant from, Instant to) {
        return List.of(new OrderInfo("gid://shopify/Order/900", new BigDecimal("100.00")),
            new OrderInfo("gid://shopify/Order/901", new BigDecimal("120.00")));
    }

    /** Simulate the customer checking out with the code. */
    void redeem(String code, String orderId, String total) {
        codes.get(code).usage = 1;
        orders.put(code, new OrderInfo(orderId, new BigDecimal(total)));
    }
}
