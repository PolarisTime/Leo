package com.leo.erp.auth.domain.entity;

import com.leo.erp.auth.domain.enums.RevokeReason;
import com.leo.erp.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "auth_refresh_token")
public class RefreshTokenSession extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_id", nullable = false, unique = true, length = 64)
    private String tokenId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason", length = 32)
    private RevokeReason revokeReason;

    @Column(name = "login_ip", length = 64)
    private String loginIp;

    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
