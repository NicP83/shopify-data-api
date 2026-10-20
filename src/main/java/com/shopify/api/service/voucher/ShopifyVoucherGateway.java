package com.shopify.api.service.voucher;

import com.shopify.api.client.ShopifyGraphQLClient;
import com.shopify.api.model.GraphQLRequest;
import com.shopify.api.model.GraphQLResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@link VoucherShopifyGateway} over the Admin GraphQL API (needs read_products, read_orders, write_discounts). */
@Component
@Slf4j
public class ShopifyVoucherGateway implements VoucherShopifyGateway {

    private static final String VARIANTS = """
        query VoucherVariants($ids: [ID!]!) {
          nodes(ids: $ids) {
            ... on ProductVariant {
              id price compareAtPrice availableForSale
              product { id vendor productType tags status }
            }
          }
        }""";

    private static final String CREATE = """
        mutation VoucherCreate($d: DiscountCodeBasicInput!) {
          discountCodeBasicCreate(basicCodeDiscount: $d) {
            codeDiscountNode { id }
            userErrors { field code message }
          }
        }""";

    private static final String LOOKUP = """
        query VoucherLookup($code: String!) {
          codeDiscountNodeByCode(code: $code) {
            id
            codeDiscount { __typename ... on DiscountCodeBasic { asyncUsageCount } }
          }
        }""";

    private static final String ORDER_BY_CODE = """
        query VoucherOrder($q: String!) {
          orders(first: 1, query: $q) {
            edges { node { id currentTotalPriceSet { shopMoney { amount } } } }
          }
        }""";

    private static final String DELETE = """
        mutation VoucherDelete($id: ID!) {
          discountCodeDelete(id: $id) { deletedCodeDiscountId userErrors { field code message } }
        }""";

    private static final String SCOPES = "{ currentAppInstallation { accessScopes { handle } } }";

    private static final String ORDERS_BETWEEN = """
        query VoucherStoreOrders($q: String!) {
          orders(first: 250, query: $q) {
            edges { node { id currentTotalPriceSet { shopMoney { amount } } } }
          }
        }""";

    private final ShopifyGraphQLClient client;

    public ShopifyVoucherGateway(ShopifyGraphQLClient client) {
        this.client = client;
    }

    @Override
    public List<VariantInfo> fetchVariants(List<String> variantGids) {
        Map<String, Object> data = call(VARIANTS, Map.of("ids", variantGids));
        List<VariantInfo> out = new ArrayList<>();
        for (Object o : list(data.get("nodes"))) {
            Map<String, Object> v = map(o);
            if (v == null || v.get("id") == null) continue; // unknown id or not a variant
            Map<String, Object> p = map(v.get("product"));
            if (p == null) continue;
            List<String> tags = new ArrayList<>();
            for (Object t : list(p.get("tags"))) tags.add(String.valueOf(t));
            out.add(new VariantInfo(
                String.valueOf(v.get("id")),
                String.valueOf(p.get("id")),
                decimal(v.get("price")),
                decimal(v.get("compareAtPrice")),
                str(p.get("vendor")),
                str(p.get("productType")),
                tags,
                Boolean.TRUE.equals(v.get("availableForSale")),
                str(p.get("status"))));
        }
        return out;
    }

    @Override
    public CreateResult createCode(CodeSpec spec) {
        Map<String, Object> input = new HashMap<>();
        input.put("title", spec.title());
        input.put("code", spec.code());
        input.put("startsAt", spec.startsAt().toString());
        input.put("endsAt", spec.endsAt().toString());
        input.put("customerSelection", Map.of("all", true));
        input.put("customerGets", Map.of(
            "value", Map.of("discountAmount", Map.of(
                "amount", VoucherRules.money(spec.amount()),
                "appliesOnEachItem", false)),
            "items", Map.of("products", Map.of("productVariantsToAdd", spec.variantGids()))));
        input.put("minimumRequirement", Map.of("subtotal",
            Map.of("greaterThanOrEqualToSubtotal", VoucherRules.money(spec.minSubtotal()))));
        input.put("usageLimit", 1);
        input.put("appliesOncePerCustomer", true);
        input.put("combinesWith", Map.of(
            "orderDiscounts", false, "productDiscounts", false, "shippingDiscounts", false));

        GraphQLResponse response = client.executeQuery(new GraphQLRequest(CREATE, Map.of("d", input)));
        if (response == null) throw new GatewayException("no response");
        if (response.hasErrors()) {
            String msg = String.valueOf(response.getErrors().get(0).getMessage());
            if (isAccessDenied(response)) return new CreateResult(false, null, true, msg);
            throw new GatewayException(msg);
        }
        Map<String, Object> payload = map(map(response.getData()).get("discountCodeBasicCreate"));
        List<Object> userErrors = list(payload == null ? null : payload.get("userErrors"));
        if (!userErrors.isEmpty()) {
            return new CreateResult(false, null, false, String.valueOf(map(userErrors.get(0)).get("message")));
        }
        Map<String, Object> node = map(payload.get("codeDiscountNode"));
        if (node == null || node.get("id") == null) throw new GatewayException("no discount id returned");
        return new CreateResult(true, String.valueOf(node.get("id")), false, null);
    }

    @Override
    public CodeState lookupCode(String code) {
        Map<String, Object> data = call(LOOKUP, Map.of("code", code));
        Map<String, Object> node = map(data.get("codeDiscountNodeByCode"));
        if (node == null) return new CodeState(false, null, 0);
        Map<String, Object> discount = map(node.get("codeDiscount"));
        Object count = discount == null ? null : discount.get("asyncUsageCount");
        int usage = count instanceof Number n ? n.intValue() : 0;
        return new CodeState(true, String.valueOf(node.get("id")), usage);
    }

    @Override
    public Optional<OrderInfo> findOrderByCode(String code) {
        Map<String, Object> data = call(ORDER_BY_CODE, Map.of("q", "discount_code:" + code));
        List<OrderInfo> orders = orders(data);
        return orders.isEmpty() ? Optional.empty() : Optional.of(orders.get(0));
    }

    @Override
    public boolean deleteCode(String discountId) {
        Map<String, Object> data = call(DELETE, Map.of("id", discountId));
        Map<String, Object> payload = map(data.get("discountCodeDelete"));
        if (payload == null) return false;
        if (!list(payload.get("userErrors")).isEmpty()) {
            log.warn("voucher: discountCodeDelete {} userErrors {}", discountId, payload.get("userErrors"));
            return false;
        }
        return payload.get("deletedCodeDiscountId") != null;
    }

    @Override
    public Optional<Boolean> hasWriteDiscountsScope() {
        try {
            Map<String, Object> data = call(SCOPES, Map.of());
            Map<String, Object> install = map(data.get("currentAppInstallation"));
            if (install == null) return Optional.empty();
            for (Object s : list(install.get("accessScopes"))) {
                if ("write_discounts".equals(map(s).get("handle"))) return Optional.of(true);
            }
            return Optional.of(false);
        } catch (GatewayException e) {
            log.warn("voucher: scope check failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<OrderInfo> ordersBetween(Instant from, Instant to) {
        String q = "created_at:>='" + from + "' AND created_at:<'" + to + "'";
        return orders(call(ORDERS_BETWEEN, Map.of("q", q)));
    }

    private List<OrderInfo> orders(Map<String, Object> data) {
        List<OrderInfo> out = new ArrayList<>();
        Map<String, Object> conn = map(data.get("orders"));
        if (conn == null) return out;
        for (Object e : list(conn.get("edges"))) {
            Map<String, Object> node = map(map(e).get("node"));
            if (node == null) continue;
            Map<String, Object> money = map(map(map(node.get("currentTotalPriceSet")).get("shopMoney")));
            out.add(new OrderInfo(String.valueOf(node.get("id")), money == null ? null : decimal(money.get("amount"))));
        }
        return out;
    }

    private Map<String, Object> call(String query, Map<String, Object> variables) {
        GraphQLResponse response = client.executeQuery(new GraphQLRequest(query, variables));
        if (response == null) throw new GatewayException("no response");
        if (response.hasErrors()) throw new GatewayException(String.valueOf(response.getErrors().get(0).getMessage()));
        if (response.getData() == null) throw new GatewayException("empty response");
        return response.getData();
    }

    private static boolean isAccessDenied(GraphQLResponse response) {
        for (GraphQLResponse.GraphQLError err : response.getErrors()) {
            Object code = err.getExtensions() == null ? null : err.getExtensions().get("code");
            String msg = String.valueOf(err.getMessage()).toLowerCase();
            if ("ACCESS_DENIED".equals(code) || msg.contains("access denied") || msg.contains("write_discounts")) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {
        return o instanceof List<?> l ? (List<Object>) l : List.of();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static BigDecimal decimal(Object o) {
        if (o == null) return null;
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
