package com.shopify.api.service.voucher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Pure voucher arithmetic. Hard ceilings here are code constants: no DB value,
 * override or chat message can exceed them.
 */
public final class VoucherRules {

    public static final BigDecimal STEP = new BigDecimal("5");
    /** Largest single voucher, even with an owner override. */
    public static final BigDecimal HARD_MAX_AMOUNT = new BigDecimal("100");
    /** A voucher is never more than this share of the eligible subtotal, even with an override. */
    public static final BigDecimal HARD_MAX_PCT = new BigDecimal("0.5");
    /** Largest settings daily pool. */
    public static final BigDecimal HARD_MAX_POOL = new BigDecimal("500");
    /** Largest owner pool raise for one day. */
    public static final BigDecimal HARD_MAX_POOL_RAISE = new BigDecimal("500");
    public static final BigDecimal HARD_MAX_SETTINGS_PCT = new BigDecimal("0.25");
    public static final BigDecimal HARD_MIN_SUBTOTAL = new BigDecimal("50");
    public static final int HARD_MAX_VALIDITY_MINUTES = 240;
    public static final int MIN_VALIDITY_MINUTES = 15;
    public static final int MAX_LINES = 30;
    public static final int MAX_QTY_PER_LINE = 20;

    private static final char[] CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final int CODE_RANDOM_CHARS = 14;
    private static final SecureRandom RANDOM = new SecureRandom();

    private VoucherRules() {}

    /** Round down to a whole multiple of $5 (negative and null become 0). */
    public static BigDecimal floorToStep(BigDecimal value) {
        if (value == null || value.signum() <= 0) return BigDecimal.ZERO;
        return value.divide(STEP, 0, RoundingMode.FLOOR).multiply(STEP);
    }

    /** floor(pct x subtotal) to the nearest $5. */
    public static BigDecimal pctCeiling(BigDecimal eligibleSubtotal, BigDecimal pct) {
        return floorToStep(eligibleSubtotal.multiply(pct));
    }

    /**
     * Minimum subtotal to put on the Shopify code: the configured minimum, raised
     * so that amount <= pct x subtotal holds at checkout too. Checkout therefore
     * enforces the 10% rule even if the model passed a cart the customer doesn't have.
     */
    public static BigDecimal codeMinSubtotal(BigDecimal amount, BigDecimal pct, BigDecimal minSubtotal) {
        BigDecimal byPct = amount.divide(pct, 0, RoundingMode.CEILING);
        return minSubtotal.max(byPct).setScale(2, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /** Deterministic per session: the same chat is always in (or out of) the holdout. */
    public static boolean inHoldout(String sessionId, int holdoutPct) {
        if (holdoutPct <= 0) return false;
        if (holdoutPct >= 100) return true;
        byte[] h = sha256Bytes("voucher-holdout:" + sessionId);
        long bucket = ((h[0] & 0xFFL) << 24 | (h[1] & 0xFFL) << 16 | (h[2] & 0xFFL) << 8 | (h[3] & 0xFFL)) % 100;
        return bucket < holdoutPct;
    }

    /** "HH" + 14 chars from a 31-symbol alphabet (~69 bits from SecureRandom). */
    public static String generateCode() {
        StringBuilder sb = new StringBuilder("HH");
        for (int i = 0; i < CODE_RANDOM_CHARS; i++) {
            sb.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    public static String sha256(String value) {
        return HexFormat.of().formatHex(sha256Bytes(value));
    }

    private static byte[] sha256Bytes(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** "$10" for whole dollars, "$12.50" otherwise. */
    public static String dollars(BigDecimal value) {
        BigDecimal v = value.stripTrailingZeros();
        return "$" + (v.scale() <= 0 ? v.toPlainString() : value.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }
}
