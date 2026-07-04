package com.leo.erp.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.config.RedisTuningProperties;
import com.leo.erp.security.support.SecurityPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void shouldPassThroughWhenBearerPrefixMissing() throws ServletException, IOException {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                new ThrowingJwtTokenService(),
                authenticatedUserCacheService(Optional.empty(), new AtomicBoolean()),
                new NoOpBlacklistService(),
                new NoOpSessionActivityService(),
                new ObjectMapper()
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldPassThroughWhenBearerTokenLooksLikeApiKey() throws ServletException, IOException {
        AtomicBoolean repositoryTouched = new AtomicBoolean(false);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                new ThrowingJwtTokenService(),
                authenticatedUserCacheService(Optional.empty(), repositoryTouched),
                new NoOpBlacklistService(),
                new NoOpSessionActivityService(),
                new ObjectMapper()
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer leo_valid-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(repositoryTouched.get()).isFalse();
    }

    @Test
    void shouldPassThroughWhenAuthenticationAlreadyExists() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("user", null, List.of())
        );
        try {
            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                    new ThrowingJwtTokenService(),
                    authenticatedUserCacheService(Optional.empty(), new AtomicBoolean()),
                    new NoOpBlacklistService(),
                    new NoOpSessionActivityService(),
                    new ObjectMapper()
            );

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer some-token");
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean chainInvoked = new AtomicBoolean(false);
            FilterChain chain = (req, res) -> chainInvoked.set(true);

            filter.doFilter(request, response, chain);

            assertThat(chainInvoked.get()).isTrue();
            assertThat(response.getStatus()).isEqualTo(200);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void shouldAuthenticateUserWhenTokenIsValidAndPrincipalFound() throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        SecurityPrincipal principal = SecurityPrincipal.authenticated(
                42L, "testuser", List.of(new SimpleGrantedAuthority("ROLE_USER")), true, false
        );
        AtomicBoolean sessionTouched = new AtomicBoolean(false);
        AtomicReference<String> touchedSessionId = new AtomicReference<>();

        Claims claims = Jwts.claims().add("uid", 42L).add("sid", "sess-abc").build();

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                new StubJwtTokenService(claims),
                authenticatedUserCacheService(Optional.of(principal), new AtomicBoolean()),
                new NoOpBlacklistService(),
                new StubSessionActivityService(sessionTouched, touchedSessionId),
                new ObjectMapper()
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(principal);
        assertThat(sessionTouched.get()).isTrue();
        assertThat(touchedSessionId.get()).isEqualTo("sess-abc");
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNotAuthenticateWhenPrincipalNotFoundInCache() throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        Claims claims = Jwts.claims().add("uid", 99L).add("sid", "sess-xyz").build();

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                new StubJwtTokenService(claims),
                authenticatedUserCacheService(Optional.empty(), new AtomicBoolean()),
                new NoOpBlacklistService(),
                new NoOpSessionActivityService(),
                new ObjectMapper()
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnUnauthorizedWhenSessionIsBlacklisted() throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        Claims claims = Jwts.claims().add("uid", 1L).add("sid", "blacklisted-session").build();

        AccessTokenBlacklistService blacklistService = new SessionBlacklistService();

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                new StubJwtTokenService(claims),
                authenticatedUserCacheService(Optional.of(SecurityPrincipal.system()), new AtomicBoolean()),
                blacklistService,
                new NoOpSessionActivityService(),
                new ObjectMapper()
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("会话已失效，请重新登录");
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnUnauthorizedWhenUserBlacklistedAndTokenIsOld() throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        Claims claims = Jwts.claims()
                .add("uid", 2L)
                .add("sid", "sess-old")
                .add(Claims.ISSUED_AT, new Date(1000L))
                .build();

        AccessTokenBlacklistService blacklistService = new UserBlacklistService(5000L);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                new StubJwtTokenService(claims),
                authenticatedUserCacheService(Optional.of(SecurityPrincipal.system()), new AtomicBoolean()),
                blacklistService,
                new NoOpSessionActivityService(),
                new ObjectMapper()
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPassThroughWhenUserBlacklistedButTokenIsNew() throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        SecurityPrincipal principal = SecurityPrincipal.authenticated(3L, "newuser", List.of());
        Claims claims = Jwts.claims()
                .add("uid", 3L)
                .add("sid", "sess-new")
                .add(Claims.ISSUED_AT, new Date(10000L))
                .build();

        AccessTokenBlacklistService blacklistService = new UserBlacklistService(5000L);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                new StubJwtTokenService(claims),
                authenticatedUserCacheService(Optional.of(principal), new AtomicBoolean()),
                blacklistService,
                new NoOpSessionActivityService(),
                new ObjectMapper()
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldHandleClaimsMissingUserIdGracefully() throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        Claims claims = Jwts.claims().add("sid", "sess-no-uid").build();

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                new StubJwtTokenService(claims),
                authenticatedUserCacheService(Optional.empty(), new AtomicBoolean()),
                new NoOpBlacklistService(),
                new NoOpSessionActivityService(),
                new ObjectMapper()
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateWhenSessionIdClaimMissing() throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        SecurityPrincipal principal = SecurityPrincipal.authenticated(5L, "nosid", List.of());
        Claims claims = Jwts.claims().add("uid", 5L).build();
        AtomicReference<String> touchedSessionId = new AtomicReference<>("not-touched");

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                new StubJwtTokenService(claims),
                authenticatedUserCacheService(Optional.of(principal), new AtomicBoolean()),
                new NoOpBlacklistService(),
                new StubSessionActivityService(new AtomicBoolean(), touchedSessionId),
                new ObjectMapper()
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainInvoked.set(true));

        assertThat(chainInvoked.get()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(touchedSessionId.get()).isNull();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNotBlacklistWhenIssuedAtIsNull() throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        SecurityPrincipal principal = SecurityPrincipal.authenticated(4L, "noiat", List.of());
        Claims claims = Jwts.claims().add("uid", 4L).add("sid", "sess-no-iat").build();

        AccessTokenBlacklistService blacklistService = new UserBlacklistService(5000L);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                new StubJwtTokenService(claims),
                authenticatedUserCacheService(Optional.of(principal), new AtomicBoolean()),
                blacklistService,
                new NoOpSessionActivityService(),
                new ObjectMapper()
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked.get()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        SecurityContextHolder.clearContext();
    }

    private AuthenticatedUserCacheService authenticatedUserCacheService(
            Optional<SecurityPrincipal> principal,
            AtomicBoolean touched
    ) {
        return new AuthenticatedUserCacheService(null, null, null, null, new RedisTuningProperties()) {
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
        public Claims parseAccessToken(String token) {
            throw new JwtException("expired");
        }
    }

    private static final class StubJwtTokenService extends JwtTokenService {

        private final Claims claims;

        private StubJwtTokenService(Claims claims) {
            super(null, null);
            this.claims = claims;
        }

        @Override
        public Claims parseAccessToken(String token) {
            return claims;
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

    private static final class SessionBlacklistService extends AccessTokenBlacklistService {

        private SessionBlacklistService() {
            super(null, null);
        }

        @Override
        public boolean isBlacklisted(Long userId) {
            return false;
        }

        @Override
        public boolean isSessionBlacklisted(String sessionId) {
            return true;
        }

        @Override
        public long getBlacklistTime(Long userId) {
            return 0L;
        }
    }

    private static final class UserBlacklistService extends AccessTokenBlacklistService {

        private final long blacklistTime;

        private UserBlacklistService(long blacklistTime) {
            super(null, null);
            this.blacklistTime = blacklistTime;
        }

        @Override
        public boolean isBlacklisted(Long userId) {
            return true;
        }

        @Override
        public boolean isSessionBlacklisted(String sessionId) {
            return false;
        }

        @Override
        public long getBlacklistTime(Long userId) {
            return blacklistTime;
        }
    }

    private static final class NoOpSessionActivityService extends SessionActivityService {

        private NoOpSessionActivityService() {
            super(null, new RedisTuningProperties());
        }

        @Override
        public void touchSession(String sessionId) {
        }
    }

    private static final class StubSessionActivityService extends SessionActivityService {

        private final AtomicBoolean touched;
        private final AtomicReference<String> capturedSessionId;

        private StubSessionActivityService(AtomicBoolean touched, AtomicReference<String> capturedSessionId) {
            super(null, new RedisTuningProperties());
            this.touched = touched;
            this.capturedSessionId = capturedSessionId;
        }

        @Override
        public void touchSession(String sessionId) {
            touched.set(true);
            capturedSessionId.set(sessionId);
        }
    }
}
