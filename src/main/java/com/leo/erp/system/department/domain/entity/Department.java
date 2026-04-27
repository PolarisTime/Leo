package com.leo.erp.system.department.domain.entity;

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
@Table(name = "sys_department")
public class Department extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "department_code", nullable = false, length = 64)
    private String departmentCode;

    @Column(name = "department_name", nullable = false, length = 128)
    private String departmentName;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "manager_name", length = 64)
    private String managerName;

    @Column(name = "contact_phone", length = 32)
    private String contactPhone;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
