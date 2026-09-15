package com.leo.erp.security.rbac.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import com.leo.erp.common.support.StatusConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * RBAC0 角色。
 *
 * <p>内置角色（{@link #builtin} 为 {@code true}）禁止删除或修改编码，避免系统管理员被锁死。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "sys_role")
public class SysRole extends AbstractAuditableEntity {

    public static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    @Id
    private Long id;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "builtin", nullable = false)
    private boolean builtin = false;

    @Column(name = "status", nullable = false, length = 16)
    private String status = StatusConstants.NORMAL;
}
