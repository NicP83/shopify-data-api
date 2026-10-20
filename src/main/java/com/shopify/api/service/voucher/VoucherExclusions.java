package com.shopify.api.service.voucher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Admin-editable exclusion list: items matching any vendor, product type or tag never count. */
public record VoucherExclusions(List<String> vendors, List<String> productTypes, List<String> tags) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static VoucherExclusions empty() {
        return new VoucherExclusions(List.of(), List.of(), List.of());
    }

    public static VoucherExclusions parse(String json) {
        if (json == null || json.isBlank()) return empty();
        try {
            return fromNode(MAPPER.readTree(json));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid exclusions JSON");
        }
    }

    public static VoucherExclusions fromNode(JsonNode node) {
        if (node == null || node.isNull()) return empty();
        return new VoucherExclusions(list(node.get("vendors")), list(node.get("productTypes")), list(node.get("tags")));
    }

    private static List<String> list(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                String v = n.asText("").trim();
                if (!v.isEmpty() && v.length() <= 255 && !out.contains(v)) out.add(v);
            }
        }
        if (out.size() > 200) throw new IllegalArgumentException("Too many exclusions");
        return List.copyOf(out);
    }

    public String toJson() {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("vendors", array(vendors));
        root.set("productTypes", array(productTypes));
        root.set("tags", array(tags));
        return root.toString();
    }

    private static ArrayNode array(List<String> values) {
        ArrayNode a = MAPPER.createArrayNode();
        values.forEach(a::add);
        return a;
    }

    public boolean excludes(String vendor, String productType, List<String> productTags) {
        if (matches(vendors, vendor) || matches(this.productTypes, productType)) return true;
        if (productTags != null) {
            for (String t : productTags) {
                if (matches(tags, t)) return true;
            }
        }
        return false;
    }

    private static boolean matches(List<String> list, String value) {
        if (value == null) return false;
        String v = value.trim().toLowerCase(Locale.ROOT);
        for (String s : list) {
            if (s.toLowerCase(Locale.ROOT).equals(v)) return true;
        }
        return false;
    }

    public boolean isEmpty() {
        return vendors.isEmpty() && productTypes.isEmpty() && tags.isEmpty();
    }
}
