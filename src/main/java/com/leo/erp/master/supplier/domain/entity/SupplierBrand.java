package com.leo.erp.master.supplier.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 供应商经营品牌: 只保存品牌名称字符串, 品牌取值来自商品资料去重集合, 不建品牌主数据。
 * 同一供应商下品牌名唯一; created_at 由数据库默认值填充, 实体只读映射。
 */
@Getter
@Setter
@Entity
@Table(name = "md_supplier_brand")
public class SupplierBrand {

    @Id
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "brand_name", nullable = false, length = 64)
    private String brandName;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
