package com.leo.erp.auth.domain.entity;

import com.leo.erp.auth.domain.enums.RevokeReason;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenSessionTest {

    @Test
    void shouldSetAndGetAllFields() {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setId(1L);
        session.setUserId(100L);
        session.setTokenId("token-uuid-001");
        session.setTokenHash("hashed_token_value");
        session.setPreviousTokenHash("previous_hash");
        session.setPreviousTokenValidUntil(LocalDateTime.of(2026, 6, 1, 0, 0));
        session.setExpiresAt(LocalDateTime.of(2026, 7, 1, 0, 0));
        session.setRevokedAt(null);
        session.setRevokeReason(null);
        session.setLoginIp("192.168.1.100");
        session.setDeviceInfo("Chrome/Windows");

        assertThat(session.getId()).isEqualTo(1L);
        assertThat(session.getUserId()).isEqualTo(100L);
        assertThat(session.getTokenId()).isEqualTo("token-uuid-001");
        assertThat(session.getTokenHash()).isEqualTo("hashed_token_value");
        assertThat(session.getPreviousTokenHash()).isEqualTo("previous_hash");
        assertThat(session.getPreviousTokenValidUntil()).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(session.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(session.getRevokedAt()).isNull();
        assertThat(session.getRevokeReason()).isNull();
        assertThat(session.getLoginIp()).isEqualTo("192.168.1.100");
        assertThat(session.getDeviceInfo()).isEqualTo("Chrome/Windows");
    }

    @Test
    void shouldHandleNullValues() {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setId(null);
        session.setUserId(null);
        session.setTokenId(null);
        session.setTokenHash(null);
        session.setPreviousTokenHash(null);
        session.setPreviousTokenValidUntil(null);
        session.setExpiresAt(null);
        session.setRevokedAt(null);
        session.setRevokeReason(null);
        session.setLoginIp(null);
        session.setDeviceInfo(null);

        assertThat(session.getId()).isNull();
        assertThat(session.getUserId()).isNull();
        assertThat(session.getTokenId()).isNull();
        assertThat(session.getTokenHash()).isNull();
        assertThat(session.getPreviousTokenHash()).isNull();
        assertThat(session.getPreviousTokenValidUntil()).isNull();
        assertThat(session.getExpiresAt()).isNull();
        assertThat(session.getRevokedAt()).isNull();
        assertThat(session.getRevokeReason()).isNull();
        assertThat(session.getLoginIp()).isNull();
        assertThat(session.getDeviceInfo()).isNull();
    }

    @Test
    void shouldReturnFalseWhenNotRevoked() {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setRevokedAt(null);

        assertThat(session.isRevoked()).isFalse();
    }

    @Test
    void shouldReturnTrueWhenRevoked() {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setRevokedAt(LocalDateTime.now());
        session.setRevokeReason(RevokeReason.MANUAL);

        assertThat(session.isRevoked()).isTrue();
    }

    @Test
    void shouldSupportAllRevokeReasons() {
        RefreshTokenSession session = new RefreshTokenSession();

        session.setRevokeReason(RevokeReason.MANUAL);
        assertThat(session.getRevokeReason()).isEqualTo(RevokeReason.MANUAL);

        session.setRevokeReason(RevokeReason.CONCURRENT_LIMIT);
        assertThat(session.getRevokeReason()).isEqualTo(RevokeReason.CONCURRENT_LIMIT);

        session.setRevokeReason(RevokeReason.EXPIRED);
        assertThat(session.getRevokeReason()).isEqualTo(RevokeReason.EXPIRED);

        session.setRevokeReason(RevokeReason.REUSE_DETECTED);
        assertThat(session.getRevokeReason()).isEqualTo(RevokeReason.REUSE_DETECTED);
    }
}
