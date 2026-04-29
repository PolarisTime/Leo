package com.leo.erp.master.warehouse.domain.entity;

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
@Table(name = "md_warehouse")
public class Warehouse extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "warehouse_code", nullable = false, unique = true, length = 64)
    private String warehouseCode;

    @Column(name = "warehouse_name", nullable = false, length = 128)
    private String warehouseName;

    @Column(name = "warehouse_type", nullable = false, length = 32)
    private String warehouseType;

    @Column(name = "contact_name", length = 64)
    private String contactName;

    @Column(name = "contact_phone", length = 32)
    private String contactPhone;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;
}
