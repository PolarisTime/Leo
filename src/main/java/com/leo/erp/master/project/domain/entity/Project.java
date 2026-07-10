package com.leo.erp.master.project.domain.entity;

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
@Table(name = "md_project")
public class Project extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "project_code", nullable = false, length = 64)
    private String projectCode;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @Column(name = "project_name_abbr", length = 100)
    private String projectNameAbbr;

    @Column(name = "project_address", length = 255)
    private String projectAddress;

    @Column(name = "project_manager", length = 32)
    private String projectManager;

    @Column(name = "customer_code", nullable = false, length = 64)
    private String customerCode;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
