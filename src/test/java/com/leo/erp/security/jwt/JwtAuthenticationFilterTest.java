package com.leo.erp.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.security.support.SecurityPrincipal;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    @Test
    void shouldReturnUnauthorizedWhenBearerTokenExpired() throws ServletException, IOException {
        AtomicBoolean repositoryTouched = new AtomicBoolean(false);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                new ThrowingJwtTokenService(),
                authenticatedUserCacheService(Optional.empty(), repositoryTouched),
                new NoOpBlacklistService(),
                new NoOpSessionActivityService(),
                new ObjectMapper()
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("登录状态已失效，请重新登录");
        assertThat(repositoryTouched.get()).isFalse();
    }

    @Test
    void shouldPassThroughWhenAuthorizationHeaderMissing() throws ServletException, IOException {
        AtomicBoolean repositoryTouched = new AtomicBoolean(false);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                new ThrowingJwtTokenService(),
                authenticatedUserCacheService(Optional.empty(), repositoryTouched),
                new NoOpBlacklistService(),
                new NoOpSessionActivityService(),
                new ObjectMapper()
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(repositoryTouched.get()).isFalse();
    }

    private AuthenticatedUserCacheService authenticatedUserCacheService(
            Optional<SecurityPrincipal> principal,
            AtomicBoolean touched
    ) {
        return new AuthenticatedUserCacheService(null, null, null, null) {
            @Override
            public Optional<SecurityPrincipal> getActivePrincipal(Long userId) {
                touched.set(true);
                return principal;
            }
        };
    }

    private static final class ThrowingJwtTokenService extends JwtTokenService {

        private ThrowingJwtTokenService() {
            super(null, null);
        }

        @Override
        public io.jsonwebtoken.Claims parseAccessToken(String token) {
            throw new JwtException("expired");
        }
    }

    private static final class NoOpBlacklistService extends AccessTokenBlacklistService {

        private NoOpBlacklistService() {
            super(null, null);
        }

        @Override
        public boolean isBlacklisted(Long userId) {
            return false;
        }

        @Override
        public boolean isSessionBlacklisted(String sessionId) {
            return false;
        }

        @Override
        public long getBlacklistTime(Long userId) {
            return 0L;
        }
    }

    private static final class NoOpSessionActivityService extends SessionActivityService {

        private NoOpSessionActivityService() {
            super(null);
        }

        @Override
        public void touchSession(String sessionId) {
        }
    }
}
