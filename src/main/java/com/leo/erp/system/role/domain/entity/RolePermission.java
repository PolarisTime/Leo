package com.leo.erp.system.role.domain.entity;

import com.leo.erp.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_role_permission")
public class RolePermission extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "resource_code", nullable = false, length = 64)
    private String resourceCode;

    @Column(name = "action_code", nullable = false, length = 32)
    private String actionCode;
}
