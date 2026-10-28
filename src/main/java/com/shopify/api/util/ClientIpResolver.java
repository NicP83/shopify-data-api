package com.shopify.api.util;

import java.util.regex.Pattern;

/**
 * Client IP behind Railway's edge, for per-IP limits. Clients control the LEFT end of
 * X-Forwarded-For, so it is never used. Order:
 *   1. X-Real-IP: Railway's edge sets it to the connecting address and overwrites any
 *      copy the caller sends (Railway staff guidance; verify with VOUCHER_LOG_IP_HEADERS).
 *   2. the RIGHT-MOST X-Forwarded-For entry (appended by the last proxy);
 *   3. the socket's remote address;
 *   4. {@link #UNKNOWN}: callers must treat this as one shared, still-limited bucket.
 * Values that don't look like an IP address are ignored.
 */
public final class ClientIpResolver {

    public static final String UNKNOWN = "unknown";

    private static final Pattern IP_LIKE = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    private ClientIpResolver() {}

    public static String resolve(String xRealIp, String xForwardedFor, String remoteAddr) {
        String real = clean(xRealIp);
        if (real != null) return real;
        if (xForwardedFor != null) {
            String[] hops = xForwardedFor.split(",");
            String last = clean(hops[hops.length - 1]);
            if (last != null) return last;
        }
        String remote = clean(remoteAddr);
        return remote != null ? remote : UNKNOWN;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.startsWith("[") && v.endsWith("]")) v = v.substring(1, v.length() - 1); // [IPv6]
        return IP_LIKE.matcher(v).matches() && (v.contains(".") || v.contains(":")) ? v : null;
    }
}
