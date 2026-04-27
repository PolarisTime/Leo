package com.leo.erp.security.jwt;

import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.securitykey.service.SecurityKeyService;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
                properties.secret(),
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
                properties.secret(),
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
                properties.secret(),
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
                .signWith(Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8)))
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
                properties.secret(),
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
                .plusMillis(properties.accessExpirationMs())
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
                .signWith(Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8)))
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
                properties.secret(),
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
                .signWith(Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8)))
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
                properties.secret(),
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
                .plusMillis(properties.accessExpirationMs())
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
                .signWith(Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThrows(JwtException.class, () -> jwtTokenService.parseAccessToken(token));
    }
}
