package com.leo.erp.system.oss.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_oss_setting")
public class OssSetting extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "storage_mode", nullable = false, length = 32)
    private String storageMode;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "endpoint", nullable = false, length = 255)
    private String endpoint;

    @Column(name = "bucket", nullable = false, length = 128)
    private String bucket;

    @Column(name = "region", nullable = false, length = 64)
    private String region;

    @Column(name = "access_key", nullable = false, length = 255)
    private String accessKey;

    @Column(name = "encrypted_secret_key", columnDefinition = "TEXT")
    private String encryptedSecretKey;

    @Column(name = "key_prefix", nullable = false, length = 255)
    private String keyPrefix;

    @Column(name = "path_style_access", nullable = false)
    private boolean pathStyleAccess;

    @Column(name = "encrypted_storage", nullable = false)
    private boolean encryptedStorage;

    @Column(name = "server_proxy_only", nullable = false)
    private boolean serverProxyOnly;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
