package com.leo.erp.system.securitykey.domain.entity;

import com.leo.erp.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "sys_security_secret")
public class SecuritySecret extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "secret_type", nullable = false, length = 32)
    private String secretType;

    @Column(name = "secret_name", nullable = false, length = 64)
    private String secretName;

    @Column(name = "key_version", nullable = false)
    private Integer keyVersion;

    @Column(name = "secret_value", nullable = false, columnDefinition = "TEXT")
    private String secretValue;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "activated_at", nullable = false)
    private LocalDateTime activatedAt;

    @Column(name = "retired_at")
    private LocalDateTime retiredAt;

    @Column(name = "remark", length = 255)
    private String remark;
}
