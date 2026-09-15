package com.leo.erp.security.rbac.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * RBAC0 角色-权限关联。
 *
 * <p>{@link #permissionCode} 允许 {@code 资源:*} 资源级通配权限码，因此数据库层
 * 未对 {@code sys_permission(code)} 建外键，合法性由服务层校验。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "sys_role_permission")
public class SysRolePermission extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "permission_code", nullable = false, length = 128)
    private String permissionCode;
}
