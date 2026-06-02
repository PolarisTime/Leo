package com.leo.erp.security.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.support.ClientIpResolver;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalRateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void allowedRequestAddsRateLimitHeadersAndContext() throws Exception {
        GlobalRateLimitFilter filter = new GlobalRateLimitFilter(
                tokenBucketService(new TokenBucketService.TokenBucketResult(true, 148, 0)),
                clientIpResolver(),
                objectMapper
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> {
            chainInvoked.set(true);
            assertThat(com.leo.erp.common.api.RateLimitContext.current(request)).isNotNull();
            assertThat(com.leo.erp.common.api.RateLimitContext.current(request).remaining()).isEqualTo(148);
        };

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isTrue();
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("150");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("148");
    }

    @Test
    void rejectedRequestIncludesRateLimitInResponseBody() throws Exception {
        GlobalRateLimitFilter filter = new GlobalRateLimitFilter(
                tokenBucketService(new TokenBucketService.TokenBucketResult(false, 0, 2_000)),
                clientIpResolver(),
                objectMapper
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainInvoked.set(true));

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(chainInvoked.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(body.path("code").asInt()).isEqualTo(4290);
        assertThat(body.path("rateLimit").path("limit").asInt()).isEqualTo(150);
        assertThat(body.path("rateLimit").path("remaining").asInt()).isZero();
        assertThat(body.path("rateLimit").path("resetSeconds").asInt()).isEqualTo(2);
        assertThat(body.path("rateLimit").path("retryAfterSeconds").asInt()).isEqualTo(2);
    }

    private TokenBucketService tokenBucketService(TokenBucketService.TokenBucketResult result) {
        return new TokenBucketService(null, null) {
            @Override
            public TokenBucketResult tryConsume(String dimensionKey, double rate, int capacity, int requested) {
                return result;
            }
        };
    }

    private ClientIpResolver clientIpResolver() {
        return new ClientIpResolver("");
    }
}
