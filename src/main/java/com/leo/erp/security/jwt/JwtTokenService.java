package com.leo.erp.security.jwt;

import com.leo.erp.security.support.SecurityPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.leo.erp.system.securitykey.service.SecurityKeyService;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;

@Service
public class JwtTokenService {

    private final JwtProperties jwtProperties;
    private final SecurityKeyService securityKeyService;

    public JwtTokenService(JwtProperties jwtProperties, SecurityKeyService securityKeyService) {
        this.jwtProperties = jwtProperties;
        this.securityKeyService = securityKeyService;
    }

    public String generateAccessToken(SecurityPrincipal principal, String sessionId) {
        Instant now = Instant.now();
        SecurityKeyService.ResolvedSecretMaterial activeMaterial = securityKeyService.getActiveJwtMaterial();
        SecretKey secretKey = secretKey(activeMaterial.secretValue());
        return Jwts.builder()
                .header()
                .keyId(String.valueOf(activeMaterial.version()))
                .and()
                .issuer(jwtProperties.issuer())
                .subject(principal.username())
                .claim("uid", principal.id())
                .claim("sid", sessionId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(jwtProperties.accessExpirationMs())))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseAccessToken(String token) {
        JwtException lastException = null;
        for (SecurityKeyService.ResolvedSecretMaterial material : securityKeyService.getJwtVerificationMaterials()) {
            try {
                Claims claims = Jwts.parser()
                        .requireIssuer(jwtProperties.issuer())
                        .verifyWith(secretKey(material.secretValue()))
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
                validateKeyWindow(material, claims);
                return claims;
            } catch (JwtException ex) {
                lastException = ex;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new IllegalStateException("未找到可用的 JWT 验签密钥");
    }

    public Long extractUserId(String token) {
        Object uid = parseAccessToken(token).get("uid");
        return uid == null ? null : Long.parseLong(String.valueOf(uid));
    }

    public String extractSessionId(String token) {
        Object sid = parseAccessToken(token).get("sid");
        return sid == null ? null : String.valueOf(sid);
    }

    public long getAccessExpirationMs() {
        return jwtProperties.accessExpirationMs();
    }

    public long getRefreshExpirationMs() {
        return jwtProperties.refreshExpirationMs();
    }

    private SecretKey secretKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private void validateKeyWindow(
            SecurityKeyService.ResolvedSecretMaterial material,
            Claims claims
    ) {
        if (material.retiredAt() == null) {
            return;
        }

        Date issuedAt = claims.getIssuedAt();
        if (issuedAt == null) {
            throw new JwtException("JWT 缺少签发时间");
        }

        Instant retiredAt = material.retiredAt()
                .atZone(ZoneId.systemDefault())
                .toInstant();
        Instant issuedAtInstant = issuedAt.toInstant();
        if (issuedAtInstant.isAfter(retiredAt)) {
            throw new JwtException("JWT 使用了已退役的签名密钥");
        }

        Date expiration = claims.getExpiration();
        if (expiration == null) {
            throw new JwtException("JWT 缺少过期时间");
        }

        Instant allowedExpiration = retiredAt.plusMillis(jwtProperties.accessExpirationMs());
        if (expiration.toInstant().isAfter(allowedExpiration)) {
            throw new JwtException("JWT 超出了历史密钥允许的有效窗口");
        }
    }
}
