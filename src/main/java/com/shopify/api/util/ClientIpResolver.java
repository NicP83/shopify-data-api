package com.shopify.api.util;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.regex.Pattern;

/**
 * Client IP behind Railway's edge, for per-IP limits. Clients control the LEFT end of
 * X-Forwarded-For, so it is never used.
 *
 * Forwarding headers are trusted only when the socket peer is a proxy-looking address
 * (private, carrier-grade NAT 100.64/10, loopback, link-local) or unknown. Railway's edge
 * connects from such addresses and doesn't publish exact ranges. A public peer is the
 * client itself, so its address is used and headers are ignored. When trusted:
 *   1. X-Real-IP: Railway's edge sets it to the connecting address and overwrites any
 *      copy the caller sends (Railway staff guidance; verify with VOUCHER_LOG_IP_HEADERS).
 *   2. the RIGHT-MOST X-Forwarded-For entry (appended by the last proxy);
 *   3. the socket's remote address;
 *   4. {@link #UNKNOWN}: callers must treat this as one shared, still-limited bucket.
 * Values must parse as IP literals (never a DNS lookup).
 */
public final class ClientIpResolver {

    public static final String UNKNOWN = "unknown";

    private static final Pattern IPV4 = Pattern.compile(
        "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    private ClientIpResolver() {}

    public static String resolve(String xRealIp, String xForwardedFor, String remoteAddr) {
        InetAddress peer = parse(remoteAddr);
        if (peer != null && !isProxyAddress(peer)) {
            return peer.getHostAddress(); // direct connection: headers are client-controlled
        }
        InetAddress real = parse(xRealIp);
        if (real != null) return real.getHostAddress();
        if (xForwardedFor != null) {
            String[] hops = xForwardedFor.split(",");
            InetAddress last = parse(hops[hops.length - 1]);
            if (last != null) return last.getHostAddress();
        }
        return peer != null ? peer.getHostAddress() : UNKNOWN;
    }

    /**
     * The key a per-IP limit should count on: IPv4 as is, IPv6 grouped to its /64
     * (one subscriber usually holds a whole /64 and can rotate within it).
     */
    public static String limitKey(String ip) {
        InetAddress a = parse(ip);
        if (a == null) return UNKNOWN;
        if (a instanceof Inet6Address) {
            byte[] b = a.getAddress();
            StringBuilder sb = new StringBuilder("v6:");
            for (int i = 0; i < 8; i += 2) {
                if (i > 0) sb.append(':');
                sb.append(Integer.toHexString(((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF)));
            }
            return sb.append("::/64").toString();
        }
        return a.getHostAddress();
    }

    /** IP literal only; null for anything else. Never resolves a hostname. */
    static InetAddress parse(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.startsWith("[") && v.endsWith("]")) v = v.substring(1, v.length() - 1); // [IPv6]
        try {
            if (IPV4.matcher(v).matches()) return InetAddress.getByName(v);
            // A string containing ':' can't be a hostname, so this never does DNS.
            if (v.contains(":") && IPV6_CHARS.matcher(v).matches()) {
                InetAddress a = InetAddress.getByName(v);
                return a instanceof Inet6Address || a instanceof Inet4Address ? a : null;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static boolean isProxyAddress(InetAddress a) {
        if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 4) {
            return (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64;     // 100.64.0.0/10 (carrier-grade NAT)
        }
        return (b[0] & 0xFE) == 0xFC;                               // fc00::/7 (unique local)
    }
}
