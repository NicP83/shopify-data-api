package com.shopify.api.handler.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.model.ChatbotConfig;
import com.shopify.api.service.voucher.VoucherChatContext;
import com.shopify.api.service.voucher.VoucherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * offer_discount: lets the storefront bot create a small one-hour closing voucher.
 * Only offered while VoucherService says vouchers are available (kill switch,
 * scope, pause). Every limit is enforced in VoucherService from server-side state;
 * this handler only parses input, and the session comes from the Reactor context
 * the storefront chat controller sets, never from the model.
 */
@Component
@Slf4j
public class OfferDiscountChatToolHandler implements ChatToolHandler {

    public static final String TOOL_NAME = "offer_discount";

    private final VoucherService voucherService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OfferDiscountChatToolHandler(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public String getToolDescription() {
        return "Create a one-time closing voucher (a fixed dollar amount off, in $5 steps) for the customer's "
            + "cart. Use ONLY to close a hesitating customer whose cart already meets the minimum spend in "
            + "full-price items, or after they add items that take it over. Never as an opener. Pass the exact cart items "
            + "(variant ids from search results, with quantities) and the amount you want to offer. The store "
            + "recalculates the cart and applies its own limits: it may give a smaller amount or say no, and "
            + "its answer is final. On success, give the customer the code and the cart link it returns.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();

        ObjectNode items = objectMapper.createObjectNode();
        items.put("type", "array");
        items.put("description", "Every item in the customer's cart");
        ObjectNode item = objectMapper.createObjectNode();
        item.put("type", "object");
        ObjectNode itemProps = objectMapper.createObjectNode();
        itemProps.set("variant_id", objectMapper.createObjectNode().put("type", "string")
            .put("description", "Variant id, e.g. gid://shopify/ProductVariant/12345 or 12345"));
        itemProps.set("quantity", objectMapper.createObjectNode().put("type", "integer").put("minimum", 1));
        item.set("properties", itemProps);
        item.set("required", objectMapper.createArrayNode().add("variant_id").add("quantity"));
        items.set("items", item);
        props.set("cart_items", items);

        props.set("amount", objectMapper.createObjectNode().put("type", "number")
            .put("description", "Voucher amount in AUD you'd like to offer, in $5 steps (e.g. 5 or 10)"));
        props.set("reason", objectMapper.createObjectNode().put("type", "string")
            .put("description", "One line on why a voucher helps close this sale"));
        props.set("customer_email", objectMapper.createObjectNode().put("type", "string")
            .put("description", "The customer's email, only if they gave it in this chat"));

        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode().add("cart_items").add("amount").add("reason");
        schema.set("required", required);
        return schema;
    }

    @Override
    public boolean isEnabled(ChatbotConfig config) {
        try {
            return voucherService.toolAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    /** e.g. "$150": the current minimum spend, for the prompt guidance. */
    public String minimumSpendLabel() {
        return voucherService.minimumSpendLabel();
    }

    @Override
    public Mono<String> execute(JsonNode input) {
        return Mono.deferContextual(ctx -> {
            VoucherChatContext chat = ctx.<VoucherChatContext>getOrEmpty(VoucherChatContext.CONTEXT_KEY).orElse(null);
            VoucherService.OfferInput offer = parse(input);
            return Mono.fromCallable(() -> voucherService.offer(offer, chat))
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::toJson);
        }).onErrorResume(e -> {
            log.error("offer_discount failed: {}", e.getMessage(), e);
            return Mono.just("{\"status\":\"refused\",\"reason_code\":\"ERROR\","
                + "\"message\":\"I couldn't create a voucher just now, so no voucher this time.\"}");
        });
    }

    private VoucherService.OfferInput parse(JsonNode input) {
        List<VoucherService.RawLine> lines = new ArrayList<>();
        JsonNode arr = input == null ? null : input.get("cart_items");
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                String id = n.hasNonNull("variant_id") ? n.get("variant_id").asText() : null;
                lines.add(new VoucherService.RawLine(id, number(n.get("quantity"))));
            }
        }
        return new VoucherService.OfferInput(lines, number(input == null ? null : input.get("amount")),
            text(input, "reason"), text(input, "customer_email"));
    }

    private static BigDecimal number(JsonNode n) {
        if (n == null || n.isNull()) return null;
        try {
            if (n.isNumber()) return n.decimalValue();
            String s = n.asText().replace("$", "").trim();
            return s.isEmpty() ? null : new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(JsonNode input, String field) {
        return input != null && input.hasNonNull(field) ? input.get(field).asText() : null;
    }

    private String toJson(VoucherService.OfferResult result) {
        ObjectNode out = objectMapper.createObjectNode();
        out.put("status", result.status());
        for (Map.Entry<String, Object> e : result.fields().entrySet()) {
            out.set(e.getKey(), objectMapper.valueToTree(e.getValue()));
        }
        return out.toString();
    }
}
