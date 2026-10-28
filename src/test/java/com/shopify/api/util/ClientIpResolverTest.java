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
    void ipv6AndFallbacks() {
        assertThat(ClientIpResolver.resolve("[2001:db8::1]", null, null)).isEqualTo("2001:db8::1");
        assertThat(ClientIpResolver.resolve(null, null, "10.0.0.2")).isEqualTo("10.0.0.2");
        assertThat(ClientIpResolver.resolve(null, null, null)).isEqualTo(ClientIpResolver.UNKNOWN);
    }
}
