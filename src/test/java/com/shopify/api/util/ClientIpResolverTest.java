package com.shopify.api.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void spoofedLeftMostForwardedForIsIgnored() {
        // The client sent "6.6.6.6"; Railway's edge appended the real address on the right.
        assertThat(ClientIpResolver.resolve(null, "6.6.6.6, 203.0.113.5", null)).isEqualTo("203.0.113.5");
        assertThat(ClientIpResolver.resolve(null, "1.1.1.1,2.2.2.2, 3.3.3.3 ,203.0.113.5", null)).isEqualTo("203.0.113.5");
    }

    @Test
    void realIpHeaderWinsOverForwardedFor() {
        assertThat(ClientIpResolver.resolve("198.51.100.4", "6.6.6.6, 203.0.113.5", "10.0.0.2")).isEqualTo("198.51.100.4");
    }

    @Test
    void rotatingTheSpoofedEntryDoesNotChangeTheResult() {
        String a = ClientIpResolver.resolve(null, "9.9.9.1, 203.0.113.5", null);
        String b = ClientIpResolver.resolve(null, "9.9.9.2, 203.0.113.5", null);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void garbageValuesAreIgnored() {
        assertThat(ClientIpResolver.resolve("not-an-ip", "6.6.6.6, <script>", "10.0.0.2")).isEqualTo("10.0.0.2");
        assertThat(ClientIpResolver.resolve("", " , ", null)).isEqualTo(ClientIpResolver.UNKNOWN);
        assertThat(ClientIpResolver.resolve("1234", null, null)).isEqualTo(ClientIpResolver.UNKNOWN);
    }

    @Test
    void headersAreIgnoredWhenThePeerIsAPublicAddress() {
        // Someone reaching the app directly (not via the edge) can't pick their IP.
        assertThat(ClientIpResolver.resolve("1.1.1.1", "2.2.2.2", "203.0.113.77")).isEqualTo("203.0.113.77");
    }

    @Test
    void headersAreTrustedFromTheEdgesPrivateOrCgnatAddresses() {
        assertThat(ClientIpResolver.resolve("198.51.100.4", null, "100.64.12.3")).isEqualTo("198.51.100.4");
        assertThat(ClientIpResolver.resolve("198.51.100.4", null, "10.1.2.3")).isEqualTo("198.51.100.4");
        assertThat(ClientIpResolver.resolve("198.51.100.4", null, "127.0.0.1")).isEqualTo("198.51.100.4");
        // 100.128.x is outside 100.64/10: public, so the header is ignored.
        assertThat(ClientIpResolver.resolve("198.51.100.4", null, "100.128.0.1")).isEqualTo("100.128.0.1");
    }

    @Test
    void hostnamesAndMalformedValuesNeverParse() {
        assertThat(ClientIpResolver.parse("example.com")).isNull();
        assertThat(ClientIpResolver.parse("256.1.1.1")).isNull();
        assertThat(ClientIpResolver.parse("1.2.3")).isNull();
        assertThat(ClientIpResolver.parse(":::::")).isNull();
    }

    @Test
    void ipv6IsLimitedPerSlash64() {
        String a = ClientIpResolver.limitKey("2001:db8:1:2::1");
        String b = ClientIpResolver.limitKey("2001:db8:1:2:ffff:ffff:ffff:ffff");
        String c = ClientIpResolver.limitKey("2001:db8:1:3::1");
        assertThat(a).isEqualTo(b).isEqualTo("v6:2001:db8:1:2::/64");
        assertThat(c).isNotEqualTo(a);
        assertThat(ClientIpResolver.limitKey("203.0.113.5")).isEqualTo("203.0.113.5");
        assertThat(ClientIpResolver.limitKey("junk")).isEqualTo(ClientIpResolver.UNKNOWN);
    }

    @Test
    void ipv6AndFallbacks() {
        assertThat(ClientIpResolver.resolve("[2001:db8::1]", null, null)).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(ClientIpResolver.resolve(null, null, "10.0.0.2")).isEqualTo("10.0.0.2");
        assertThat(ClientIpResolver.resolve(null, null, null)).isEqualTo(ClientIpResolver.UNKNOWN);
    }
}
