package com.leo.erp.common.support;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTest {

    @Test
    void shouldReturnUnknown_whenRequestIsNull() {
        ClientIpResolver resolver = new ClientIpResolver("");
        assertThat(resolver.resolveClientIp(null)).isEqualTo("unknown");
        assertThat(resolver.resolveClientIpOrUnknown(null)).isEqualTo("unknown");
    }

    @Test
    void shouldReturnRemoteAddr_whenNotTrusted() {
        ClientIpResolver resolver = new ClientIpResolver("");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        request.addHeader("X-Forwarded-For", "198.51.100.10");

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("192.168.1.100");
    }

    @Test
    void shouldReturnRemoteAddr_whenTrustedButNoForwardedHeader() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldReturnForwardedClientIp_whenTrusted() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1,192.168.1.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "10.0.0.1, 192.168.1.1");

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("10.0.0.1");
    }

    @Test
    void shouldStopAtNearestUntrustedProxyWhenWalkingForwardedChainFromRight() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.10, 203.0.113.20");

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("203.0.113.20");
    }

    @Test
    void shouldFallbackToRemoteAddrWhenForwardedChainContainsHostname() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "client.example.com");

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldFallbackToRemoteAddrWhenForwardedChainExceedsEightHops() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", String.join(",", java.util.Collections.nCopies(9, "198.51.100.10")));

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldFallbackToRemoteAddrWhenForwardedHeaderIsTooLong() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "1".repeat(1025));

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldReturnRemoteAddr_whenForwardedHeaderIsBlank() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "  ");

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldReturnRemoteAddr_whenForwardedClientIpIsEmpty() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", ", 192.168.1.1");

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldReturnUnknown_whenRemoteAddrIsNull() {
        ClientIpResolver resolver = new ClientIpResolver("");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(null);

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("unknown");
    }

    @Test
    void shouldReturnUnknown_whenExceptionOccurs() {
        ClientIpResolver resolver = new ClientIpResolver("");
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public String getRemoteAddr() {
                throw new RuntimeException("error");
            }
        };

        String ip = resolver.resolveClientIpOrUnknown(request);

        assertThat(ip).isEqualTo("unknown");
    }

    @Test
    void shouldMatchExactIpAsTrusted() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "172.16.0.1");

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("172.16.0.1");
    }

    @Test
    void shouldNotMatchDifferentIpAsTrusted() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("10.0.0.2");
    }

    @Test
    void shouldMatchCidrRange() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "192.168.1.1");

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("192.168.1.1");
    }

    @Test
    void shouldNotMatchDifferentCidrRange() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.16.0.1");

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("172.16.0.1");
    }

    @Test
    void shouldHandleEmptyTrustedProxyList() {
        ClientIpResolver resolver = new ClientIpResolver(" ");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldMatchCidrWithNonZeroNetworkBits() {
        ClientIpResolver resolver = new ClientIpResolver("192.168.1.0/24");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("192.168.1.100");
    }

    @Test
    void shouldNotMatchCidrOutsideRange() {
        ClientIpResolver resolver = new ClientIpResolver("192.168.1.0/24");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.2.1");

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("192.168.2.1");
    }

    @Test
    void shouldHandleCidrWithPartialBytePrefix() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/28");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.15");
        request.addHeader("X-Forwarded-For", "172.16.0.1");

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("172.16.0.1");
    }

    @Test
    void shouldRejectCidrOutsidePartialBytePrefix() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/28");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.16");

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("10.0.0.16");
    }

    @Test
    void shouldHandleInvalidCidrIp() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("not-an-ip");

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("not-an-ip");
    }

    @Test
    void shouldRejectTrustedProxyThatIsNotAnIpLiteral() {
        assertThatThrownBy(() -> new ClientIpResolver("invalid-cidr"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid trusted proxy");
    }

    @Test
    void shouldRejectInvalidCidrAddressSpec() {
        assertThatThrownBy(() -> new ClientIpResolver("bad-host/24"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid trusted proxy");
    }

    @Test
    void shouldRejectCidrPrefixOutsideAddressSize() {
        assertThatThrownBy(() -> new ClientIpResolver("10.0.0.0/33"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid trusted proxy");
    }

    @Test
    void shouldNotMatchAddressWithDifferentFamilyThanTrustedCidr() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:db8::1");

        String ip = resolver.resolveClientIp(request);

        assertThat(ip).isEqualTo("2001:db8::1");
    }

    @Test
    void trustedProxyMatcherShouldRejectNullIp() throws Exception {
        Class<?> matcherType = Class.forName("com.leo.erp.common.support.ClientIpResolver$TrustedProxyMatcher");
        Constructor<?> constructor = matcherType.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        Object matcher = constructor.newInstance("10.0.0.0/8");
        Method matches = matcherType.getDeclaredMethod("matches", String.class);
        matches.setAccessible(true);

        assertThat((Boolean) matches.invoke(matcher, new Object[]{null})).isFalse();
    }
}
