package com.leo.erp.system.role.domain.entity;

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
@Table(name = "sys_role")
public class RoleSetting extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "role_code", nullable = false, unique = true, length = 64)
    private String roleCode;

    @Column(name = "role_name", nullable = false, length = 128)
    private String roleName;

    @Column(name = "role_type", nullable = false, length = 32)
    private String roleType;

    @Column(name = "permission_count", nullable = false)
    private Integer permissionCount;

    @Column(name = "permission_summary", length = 500)
    private String permissionSummary;

    @Column(name = "user_count", nullable = false)
    private Integer userCount;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
