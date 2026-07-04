package com.leo.erp.security.jwt;

import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.securitykey.service.SecurityKeyService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenServiceTest {

    @Test
    void shouldGenerateAndParseAccessToken() {
        JwtProperties properties = new JwtProperties(
                "leo-erp",
                "leo-erp-jwt-secret-key-2026-must-be-long-enough-for-hs512",
                1_800_000L,
                604_800_000L
        );
        SecurityKeyService securityKeyService = mock(SecurityKeyService.class);
        SecurityKeyService.ResolvedSecretMaterial active = new SecurityKeyService.ResolvedSecretMaterial(
                SecurityKeyService.SOURCE_CONFIG,
                0,
                properties.getSecret(),
                null,
                null,
                "FP-ACTIVE"
        );
        when(securityKeyService.getActiveJwtMaterial()).thenReturn(active);
        when(securityKeyService.getJwtVerificationMaterials()).thenReturn(List.of(active));
        JwtTokenService jwtTokenService = new JwtTokenService(properties, securityKeyService);
        SecurityPrincipal principal = new SecurityPrincipal(
                1001L,
                "admin",
                "encoded",
                true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        String token = jwtTokenService.generateAccessToken(principal, "session-1001");

        assertNotNull(token);
        assertEquals(1001L, jwtTokenService.extractUserId(token));
        assertEquals("session-1001", jwtTokenService.extractSessionId(token));
        assertEquals("admin", jwtTokenService.parseAccessToken(token).getSubject());
    }

    @Test
    void shouldVerifyTokenUsingRetiredKeyAfterRotation() {
        JwtProperties properties = new JwtProperties(
                "leo-erp",
                "leo-erp-jwt-secret-key-2026-must-be-long-enough-for-hs512",
                1_800_000L,
                604_800_000L
        );
        SecurityKeyService signKeyService = mock(SecurityKeyService.class);
        SecurityKeyService.ResolvedSecretMaterial oldActive = new SecurityKeyService.ResolvedSecretMaterial(
                SecurityKeyService.SOURCE_CONFIG,
                1,
                properties.getSecret(),
                null,
                null,
                "FP-OLD"
        );
        when(signKeyService.getActiveJwtMaterial()).thenReturn(oldActive);
        when(signKeyService.getJwtVerificationMaterials()).thenReturn(List.of(oldActive));
        JwtTokenService signService = new JwtTokenService(properties, signKeyService);

        SecurityPrincipal principal = new SecurityPrincipal(
                1002L,
                "rotated-admin",
                "encoded",
                true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        String token = signService.generateAccessToken(principal, "session-1002");

        SecurityKeyService verifyKeyService = mock(SecurityKeyService.class);
        SecurityKeyService.ResolvedSecretMaterial newActive = new SecurityKeyService.ResolvedSecretMaterial(
                SecurityKeyService.SOURCE_DATABASE,
                2,
                "leo-erp-jwt-secret-key-2027-rotated-signature-material-is-long-enough",
                null,
                null,
                "FP-NEW"
        );
        when(verifyKeyService.getActiveJwtMaterial()).thenReturn(newActive);
        when(verifyKeyService.getJwtVerificationMaterials()).thenReturn(List.of(newActive, oldActive));
        JwtTokenService verifyService = new JwtTokenService(properties, verifyKeyService);

        assertEquals(1002L, verifyService.extractUserId(token));
        assertEquals("session-1002", verifyService.extractSessionId(token));
        assertEquals("rotated-admin", verifyService.parseAccessToken(token).getSubject());
    }

    @Test
    void shouldPreserveExpiredTokenFailureWhenStaleWeakRetiredKeyExists() {
        JwtProperties properties = new JwtProperties(
                "leo-erp",
                "leo-erp-jwt-secret-key-2026-must-be-long-enough-for-hs512",
                1_800_000L,
                604_800_000L
        );
        SecurityKeyService.ResolvedSecretMaterial active = new SecurityKeyService.ResolvedSecretMaterial(
                SecurityKeyService.SOURCE_DATABASE,
                4,
                properties.getSecret(),
                null,
                null,
                "FP-ACTIVE"
        );
        SecurityKeyService.ResolvedSecretMaterial staleWeakRetired = new SecurityKeyService.ResolvedSecretMaterial(
                SecurityKeyService.SOURCE_DATABASE,
                1,
                "leo-erp-jwt-secret-key-weak-2026",
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now().minusDays(9),
                "FP-WEAK"
        );
        SecurityKeyService securityKeyService = mock(SecurityKeyService.class);
        when(securityKeyService.getJwtVerificationMaterials()).thenReturn(List.of(active, staleWeakRetired));
        JwtTokenService jwtTokenService = new JwtTokenService(properties, securityKeyService);

        Instant issuedAt = Instant.now().minusSeconds(3_600);
        String token = Jwts.builder()
                .header()
                .keyId("4")
                .and()
                .issuer("leo-erp")
                .subject("admin")
                .claim("uid", 1001L)
                .claim("sid", "session-1001")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThrows(ExpiredJwtException.class, () -> jwtTokenService.parseAccessToken(token));
    }

    @Test
    void shouldRejectAccessTokenWhenIssuerDoesNotMatch() {
        JwtProperties properties = new JwtProperties(
                "leo-erp",
                "leo-erp-jwt-secret-key-2026-must-be-long-enough-for-hs512",
                1_800_000L,
                604_800_000L
        );
        SecurityKeyService securityKeyService = mock(SecurityKeyService.class);
        SecurityKeyService.ResolvedSecretMaterial active = new SecurityKeyService.ResolvedSecretMaterial(
                SecurityKeyService.SOURCE_CONFIG,
                0,
                properties.getSecret(),
                null,
                null,
                "FP-ACTIVE"
        );
        when(securityKeyService.getActiveJwtMaterial()).thenReturn(active);
        when(securityKeyService.getJwtVerificationMaterials()).thenReturn(List.of(active));

        String token = Jwts.builder()
                .issuer("unexpected-issuer")
                .subject("admin")
                .claim("uid", 1001L)
                .claim("sid", "session-1001")
                .signWith(Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8)))
                .compact();

        JwtTokenService jwtTokenService = new JwtTokenService(properties, securityKeyService);

        assertThrows(JwtException.class, () -> jwtTokenService.parseAccessToken(token));
    }

    @Test
    void shouldAcceptRetiredKeyTokensIssuedBeforeRotationWithinLegacyAccessWindow() {
        JwtProperties properties = new JwtProperties(
                "leo-erp",
                "leo-erp-jwt-secret-key-2026-must-be-long-enough-for-hs512",
                1_800_000L,
                604_800_000L
        );
        LocalDateTime retiredAt = LocalDateTime.now().minusMinutes(10).withNano(0);
        SecurityKeyService.ResolvedSecretMaterial retiredKey = new SecurityKeyService.ResolvedSecretMaterial(
                SecurityKeyService.SOURCE_DATABASE,
                1,
                properties.getSecret(),
                retiredAt.minusDays(30),
                retiredAt,
                "FP-OLD"
        );
        SecurityKeyService securityKeyService = mock(SecurityKeyService.class);
        when(securityKeyService.getActiveJwtMaterial()).thenReturn(retiredKey);
        when(securityKeyService.getJwtVerificationMaterials()).thenReturn(List.of(retiredKey));
        JwtTokenService jwtTokenService = new JwtTokenService(properties, securityKeyService);

        Instant issuedAt = retiredAt.minusMinutes(5).atZone(ZoneId.systemDefault()).toInstant();
        Instant expiresAt = retiredAt
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .plusMillis(properties.getAccessExpirationMs())
                .minusSeconds(1);
        String token = Jwts.builder()
                .header()
                .keyId("1")
                .and()
                .issuer("leo-erp")
                .subject("admin")
                .claim("uid", 1001L)
                .claim("sid", "session-1001")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertEquals(1001L, jwtTokenService.extractUserId(token));
    }

    @Test
    void shouldRejectTokensIssuedAfterRetiredKeyWasRotatedOut() {
        JwtProperties properties = new JwtProperties(
                "leo-erp",
                "leo-erp-jwt-secret-key-2026-must-be-long-enough-for-hs512",
                1_800_000L,
                604_800_000L
        );
        LocalDateTime retiredAt = LocalDateTime.now().minusMinutes(10).withNano(0);
        SecurityKeyService.ResolvedSecretMaterial retiredKey = new SecurityKeyService.ResolvedSecretMaterial(
                SecurityKeyService.SOURCE_DATABASE,
                1,
                properties.getSecret(),
                retiredAt.minusDays(30),
                retiredAt,
                "FP-OLD"
        );
        SecurityKeyService securityKeyService = mock(SecurityKeyService.class);
        when(securityKeyService.getActiveJwtMaterial()).thenReturn(retiredKey);
        when(securityKeyService.getJwtVerificationMaterials()).thenReturn(List.of(retiredKey));
        JwtTokenService jwtTokenService = new JwtTokenService(properties, securityKeyService);

        Instant issuedAt = retiredAt.plusMinutes(1).atZone(ZoneId.systemDefault()).toInstant();
        Instant expiresAt = issuedAt.plusSeconds(600);
        String token = Jwts.builder()
                .header()
                .keyId("1")
                .and()
                .issuer("leo-erp")
                .subject("admin")
                .claim("uid", 1001L)
                .claim("sid", "session-1001")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThrows(JwtException.class, () -> jwtTokenService.parseAccessToken(token));
    }

    @Test
    void shouldRejectRetiredKeyTokensThatExceedLegacyAccessWindow() {
        JwtProperties properties = new JwtProperties(
                "leo-erp",
                "leo-erp-jwt-secret-key-2026-must-be-long-enough-for-hs512",
                1_800_000L,
                604_800_000L
        );
        LocalDateTime retiredAt = LocalDateTime.now().minusMinutes(10).withNano(0);
        SecurityKeyService.ResolvedSecretMaterial retiredKey = new SecurityKeyService.ResolvedSecretMaterial(
                SecurityKeyService.SOURCE_DATABASE,
                1,
                properties.getSecret(),
                retiredAt.minusDays(30),
                retiredAt,
                "FP-OLD"
        );
        SecurityKeyService securityKeyService = mock(SecurityKeyService.class);
        when(securityKeyService.getActiveJwtMaterial()).thenReturn(retiredKey);
        when(securityKeyService.getJwtVerificationMaterials()).thenReturn(List.of(retiredKey));
        JwtTokenService jwtTokenService = new JwtTokenService(properties, securityKeyService);

        Instant issuedAt = retiredAt.minusMinutes(1).atZone(ZoneId.systemDefault()).toInstant();
        Instant expiresAt = retiredAt
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .plusMillis(properties.getAccessExpirationMs())
                .plusSeconds(1);
        String token = Jwts.builder()
                .header()
                .keyId("1")
                .and()
                .issuer("leo-erp")
                .subject("admin")
                .claim("uid", 1001L)
                .claim("sid", "session-1001")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThrows(JwtException.class, () -> jwtTokenService.parseAccessToken(token));
    }

    @Test
    void shouldRejectAccessTokenWhenVerificationMaterialsAreEmpty() {
        JwtProperties properties = jwtProperties();
        SecurityKeyService securityKeyService = mock(SecurityKeyService.class);
        when(securityKeyService.getJwtVerificationMaterials()).thenReturn(List.of());
        JwtTokenService jwtTokenService = new JwtTokenService(properties, securityKeyService);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> jwtTokenService.parseAccessToken("token")
        );

        assertEquals("未找到可用的 JWT 验签密钥", failure.getMessage());
    }

    @Test
    void shouldThrowLastJwtExceptionWhenAllVerificationMaterialsFail() {
        JwtProperties properties = jwtProperties();
        SecurityKeyService.ResolvedSecretMaterial invalidMaterial = jwtMaterial(1, "short", null);
        SecurityKeyService.ResolvedSecretMaterial wrongLastMaterial = jwtMaterial(
                2,
                "leo-erp-jwt-secret-key-2027-wrong-hs384-material-enough",
                null
        );
        SecurityKeyService securityKeyService = mock(SecurityKeyService.class);
        when(securityKeyService.getJwtVerificationMaterials()).thenReturn(List.of(invalidMaterial, wrongLastMaterial));
        JwtTokenService jwtTokenService = new JwtTokenService(properties, securityKeyService);

        String token = Jwts.builder()
                .issuer("leo-erp")
                .subject("admin")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8)))
                .compact();

        JwtException failure = assertThrows(JwtException.class, () -> jwtTokenService.parseAccessToken(token));

        assertEquals(SignatureException.class, failure.getClass());
    }

    @Test
    void shouldReturnNullWhenUserIdAndSessionIdClaimsAreMissing() {
        JwtProperties properties = jwtProperties();
        SecurityKeyService.ResolvedSecretMaterial active = jwtMaterial(1, properties.getSecret(), null);
        SecurityKeyService securityKeyService = mock(SecurityKeyService.class);
        when(securityKeyService.getJwtVerificationMaterials()).thenReturn(List.of(active));
        JwtTokenService jwtTokenService = new JwtTokenService(properties, securityKeyService);

        String token = Jwts.builder()
                .issuer("leo-erp")
                .subject("admin")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertNull(jwtTokenService.extractUserId(token));
        assertNull(jwtTokenService.extractSessionId(token));
    }

    @Test
    void shouldReturnConfiguredExpirationDurations() {
        JwtProperties properties = jwtProperties(300_000L, 1_800_000L);
        JwtTokenService jwtTokenService = new JwtTokenService(properties, mock(SecurityKeyService.class));

        assertEquals(300_000L, jwtTokenService.getAccessExpirationMs());
        assertEquals(1_800_000L, jwtTokenService.getRefreshExpirationMs());
    }

    @Test
    void shouldRejectRetiredKeyTokenWithoutIssuedAt() {
        JwtProperties properties = jwtProperties();
        LocalDateTime retiredAt = LocalDateTime.now().minusMinutes(10).withNano(0);
        SecurityKeyService.ResolvedSecretMaterial retiredKey = jwtMaterial(1, properties.getSecret(), retiredAt);
        SecurityKeyService securityKeyService = mock(SecurityKeyService.class);
        when(securityKeyService.getJwtVerificationMaterials()).thenReturn(List.of(retiredKey));
        JwtTokenService jwtTokenService = new JwtTokenService(properties, securityKeyService);

        String token = Jwts.builder()
                .issuer("leo-erp")
                .subject("admin")
                .claim("uid", 1001L)
                .claim("sid", "session-1001")
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThrows(JwtException.class, () -> jwtTokenService.parseAccessToken(token));
    }

    @Test
    void shouldRejectRetiredKeyTokenWithoutExpiration() {
        JwtProperties properties = jwtProperties();
        LocalDateTime retiredAt = LocalDateTime.now().minusMinutes(10).withNano(0);
        SecurityKeyService.ResolvedSecretMaterial retiredKey = jwtMaterial(1, properties.getSecret(), retiredAt);
        SecurityKeyService securityKeyService = mock(SecurityKeyService.class);
        when(securityKeyService.getJwtVerificationMaterials()).thenReturn(List.of(retiredKey));
        JwtTokenService jwtTokenService = new JwtTokenService(properties, securityKeyService);

        Instant issuedAt = retiredAt.minusMinutes(1).atZone(ZoneId.systemDefault()).toInstant();
        String token = Jwts.builder()
                .issuer("leo-erp")
                .subject("admin")
                .claim("uid", 1001L)
                .claim("sid", "session-1001")
                .issuedAt(Date.from(issuedAt))
                .signWith(Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThrows(JwtException.class, () -> jwtTokenService.parseAccessToken(token));
    }

    private static JwtProperties jwtProperties() {
        return jwtProperties(1_800_000L, 604_800_000L);
    }

    private static JwtProperties jwtProperties(long accessExpirationMs, long refreshExpirationMs) {
        return new JwtProperties(
                "leo-erp",
                "leo-erp-jwt-secret-key-2026-must-be-long-enough-for-hs512",
                accessExpirationMs,
                refreshExpirationMs
        );
    }

    private static SecurityKeyService.ResolvedSecretMaterial jwtMaterial(
            int version,
            String secret,
            LocalDateTime retiredAt
    ) {
        return new SecurityKeyService.ResolvedSecretMaterial(
                SecurityKeyService.SOURCE_DATABASE,
                version,
                secret,
                retiredAt == null ? null : retiredAt.minusDays(30),
                retiredAt,
                "FP-" + version
        );
    }
}
