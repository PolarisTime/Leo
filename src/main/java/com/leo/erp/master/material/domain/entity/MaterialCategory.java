package com.leo.erp.master.material.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "md_material_category")
public class MaterialCategory extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "category_code", nullable = false, length = 64)
    private String categoryCode;

    @Column(name = "category_name", nullable = false, length = 64)
    private String categoryName;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "purchase_weigh_required", nullable = false)
    private Boolean purchaseWeighRequired = Boolean.FALSE;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
