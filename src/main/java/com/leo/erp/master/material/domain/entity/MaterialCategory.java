package com.leo.erp.master.material.domain.entity;

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
@Table(name = "md_material_category")
public class MaterialCategory extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "category_code", nullable = false, length = 32)
    private String categoryCode;

    @Column(name = "category_name", nullable = false, length = 64)
    private String categoryName;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
