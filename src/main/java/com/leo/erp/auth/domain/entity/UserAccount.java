package com.leo.erp.auth.domain.entity;

import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.common.persistence.AbstractAuditableEntity;
import com.leo.erp.common.persistence.StatusAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "sys_user")
public class UserAccount extends AbstractAuditableEntity implements StatusAwareEntity {

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

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UserStatus status;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "preferences_json", columnDefinition = "JSONB")
    @org.hibernate.annotations.ColumnTransformer(read = "preferences_json::text", write = "?::jsonb")
    private String preferencesJson;

    /**
     * {@link StatusAwareEntity} 契约的 String 形态状态读取：返回枚举名，与
     * {@code @Enumerated(EnumType.STRING)} 的落库值一致。
     */
    @Override
    public String getStatus() {
        return status == null ? null : status.name();
    }

    /**
     * {@link StatusAwareEntity} 契约的 String 形态状态写入；未知枚举名按非法状态拒绝，
     * 不允许把任意字符串写进受约束的状态列。
     *
     * <p>与 {@link #setStatus(UserStatus)} 重载共存：业务代码继续用枚举重载写入，既保留类型安全，
     * 也让架构门禁（按方法名 {@code setStatus} 识别状态写）仍能看见这些写点，不因契约适配而失明。</p>
     */
    @Override
    public void setStatus(String status) {
        this.status = status == null ? null : UserStatus.valueOf(status);
    }

    /** 类型安全的枚举读取：业务代码优先使用本方法，避免 String↔枚举来回转换。 */
    public UserStatus getUserStatus() {
        return status;
    }

    /** 类型安全的枚举写入（保留 {@code setStatus} 方法名，见 {@link #setStatus(String)} 的说明）。 */
    public void setStatus(UserStatus status) {
        this.status = status;
    }
}
