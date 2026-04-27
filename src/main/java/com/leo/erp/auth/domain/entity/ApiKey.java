package com.leo.erp.auth.domain.entity;

import com.leo.erp.common.persistence.AuditableEntity;
import com.leo.erp.auth.domain.enums.ApiKeyStatus;
import com.leo.erp.auth.domain.enums.ApiKeyStatusConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "auth_api_key")
public class ApiKey extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "key_name", nullable = false, length = 64)
    private String keyName;

    @Column(name = "key_prefix", nullable = false, length = 8)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, unique = true, length = 128)
    private String keyHash;

    @Column(name = "usage_scope", nullable = false, length = 32)
    private String usageScope;

    @Column(name = "allowed_resources", length = 2000)
    private String allowedResources;

    @Column(name = "allowed_actions", length = 512)
    private String allowedActions;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "status", nullable = false, length = 16)
    @Convert(converter = ApiKeyStatusConverter.class)
    private ApiKeyStatus status;

    public boolean isActive() {
        return status == ApiKeyStatus.ACTIVE && (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()));
    }
}
