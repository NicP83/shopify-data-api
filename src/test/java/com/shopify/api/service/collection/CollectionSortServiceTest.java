package com.shopify.api.service.collection;

import com.shopify.api.service.collection.CollectionSortService.ProductInfo;
import com.shopify.api.service.collection.CollectionSortService.VariantInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the pure move-computation logic that decides how sold-out
 * products are sunk to the bottom of a collection. These exercise
 * {@link CollectionSortService#computeMoves} and
 * {@link CollectionSortService#isAlreadyGrouped} without touching Shopify.
 */
class CollectionSortServiceTest {

    private static ProductInfo keep(String id) { return new ProductInfo(id, true); }
    private static ProductInfo sold(String id) { return new ProductInfo(id, false); }

    @Test
    void alreadyGrouped_kepthenSoldOut_producesNoMoves() {
        List<ProductInfo> products = List.of(keep("a"), keep("b"), sold("c"), sold("d"));
        assertTrue(CollectionSortService.isAlreadyGrouped(products));
        assertTrue(CollectionSortService.computeMoves(products).isEmpty());
    }

    @Test
    void allSellable_producesNoMoves() {
        List<ProductInfo> products = List.of(keep("a"), keep("b"), keep("c"));
        assertTrue(CollectionSortService.computeMoves(products).isEmpty());
    }

    @Test
    void allSoldOut_producesNoMoves() {
        // Nothing to sink relative to — already "grouped".
        List<ProductInfo> products = List.of(sold("a"), sold("b"));
        assertTrue(CollectionSortService.isAlreadyGrouped(products));
        assertTrue(CollectionSortService.computeMoves(products).isEmpty());
    }

    @Test
    void soldOutOnTop_sinksToBottomPreservingOrder() {
        // order: X(sold), A(keep), Y(sold), B(keep)  -> keep=[A,B], sink=[X,Y]
        List<ProductInfo> products = List.of(sold("X"), keep("A"), sold("Y"), keep("B"));
        assertFalse(CollectionSortService.isAlreadyGrouped(products));

        List<Map<String, Object>> moves = CollectionSortService.computeMoves(products);
        // Shopify applies moves sequentially: each sold-out product is moved to the
        // LAST index (n-1 = 3) in original order, which stacks them at the bottom.
        assertEquals(2, moves.size());
        assertEquals(Map.of("id", "X", "newPosition", "3"), moves.get(0));
        assertEquals(Map.of("id", "Y", "newPosition", "3"), moves.get(1));
    }

    @Test
    void singleSoldOutFirst_movesToLastIndex() {
        List<ProductInfo> products = List.of(sold("S"), keep("A"), keep("B"));
        List<Map<String, Object>> moves = CollectionSortService.computeMoves(products);
        assertEquals(1, moves.size());
        // Last index = n-1 = 2.
        assertEquals(Map.of("id", "S", "newPosition", "2"), moves.get(0));
    }

    // ---------- availability rule (isKeepOnTop) ----------

    private static CollectionSortService svc() {
        CollectionSortService s = new CollectionSortService(null);
        s.init(); // parses pre-order tags (empty CSV -> empty set here)
        return s;
    }

    private static VariantInfo variant(int qty, String policy, Boolean tracked) {
        return new VariantInfo(qty, policy, tracked);
    }

    @Test
    void inStockVariant_isKeptOnTop() {
        assertTrue(svc().isKeepOnTop(List.of(), null, List.of(variant(3, "DENY", true)), false));
    }

    @Test
    void backorderVariant_continueAtZero_isKeptOnTop() {
        // Continue-selling at zero stock == back-order, still sellable like the theme.
        assertTrue(svc().isKeepOnTop(List.of(), null, List.of(variant(0, "CONTINUE", true)), false));
    }

    @Test
    void untrackedVariant_isKeptOnTop() {
        assertTrue(svc().isKeepOnTop(List.of(), null, List.of(variant(0, "DENY", false)), false));
    }

    @Test
    void soldOut_denyTrackedZero_isNotKept() {
        assertFalse(svc().isKeepOnTop(List.of("clearance"), null, List.of(variant(0, "DENY", true)), false));
    }

    @Test
    void comingSoonTag_isKeptOnTop() {
        assertTrue(svc().isKeepOnTop(List.of("Coming Soon"), null, List.of(variant(0, "DENY", true)), false));
    }

    @Test
    void legacyPreorderTemplate_isKeptOnTop() {
        assertTrue(svc().isKeepOnTop(List.of(), "pre-order", List.of(variant(0, "DENY", true)), false));
    }

    @Test
    void truncatedVariants_conservativelyKeptOnTop() {
        // We couldn't see all variants -> never sink a possibly-sellable product.
        assertTrue(svc().isKeepOnTop(List.of(), null, List.of(variant(0, "DENY", true)), true));
    }
}
