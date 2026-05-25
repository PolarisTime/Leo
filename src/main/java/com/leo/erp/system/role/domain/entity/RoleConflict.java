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
@Table(name = "sys_role_conflict")
public class RoleConflict extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "conflict_role_id", nullable = false)
    private Long conflictRoleId;
}
