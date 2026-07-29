package com.shopify.api.util;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared logic for deciding whether a Shopify product represents a preorder.
 *
 * Hearn's Hobbies marks preorder products with a product tag (e.g. "preorder").
 * A preorder item is genuinely purchasable even though it typically sits at zero
 * inventory, so tools must treat a preorder tag as a sellable state rather than
 * reporting "out of stock" or hiding the product.
 *
 * The set of recognised preorder tags is configurable via
 * {@code chatbot.tools.preorder-tags}; matching is case-insensitive and trimmed.
 */
public final class PreorderTagUtil {

    private PreorderTagUtil() {
    }

    /**
     * Parse a comma-separated config value (e.g. "preorder,pre-order") into a
     * normalised (lower-cased, trimmed) set of preorder tag values.
     */
    public static Set<String> parsePreorderTags(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase())
                .collect(Collectors.toSet());
    }

    /**
     * @param tags         the product's tags as returned by the Shopify GraphQL node
     * @param preorderTags normalised preorder tag values (see {@link #parsePreorderTags})
     * @return true if any of the product's tags matches a configured preorder tag
     */
    public static boolean isPreorder(List<String> tags, Set<String> preorderTags) {
        if (tags == null || tags.isEmpty() || preorderTags == null || preorderTags.isEmpty()) {
            return false;
        }
        for (String tag : tags) {
            if (tag != null && preorderTags.contains(tag.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
