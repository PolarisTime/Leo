package com.leo.erp.auth.domain.entity;

import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "sys_user")
public class UserAccount extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Version
    private Long version;

    @Column(name = "login_name", nullable = false, unique = true, length = 64)
    private String loginName;

    @Column(name = "password_hash", nullable = false, length = 128)
    private String passwordHash;

    @Column(name = "credential_version", nullable = false)
    private Long credentialVersion = 0L;

    @Column(name = "user_name", nullable = false, length = 64)
    private String userName;

    @Column(name = "mobile", length = 20)
    private String mobile;

    @Column(name = "last_login_date")
    private LocalDateTime lastLoginDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UserStatus status;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "preferences_json", columnDefinition = "JSONB")
    @org.hibernate.annotations.ColumnTransformer(read = "preferences_json::text", write = "?::jsonb")
    private String preferencesJson;
}
