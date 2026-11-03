package com.leo.erp.master.supplier.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 供应商品牌规格配置: 某供应商某品牌允许的「类别 + 材质 + 规格 + 定尺」组合。
 *
 * <p>只存「被勾选保留」的组合, 不存「允许哪些」的规则表: 该品牌没有任何未删除行即表示回落到
 * 「按商品资料筛出的该品牌全量组合」, 由调用方(报价表)自行解释空集语义, 后端不做默认值展开。</p>
 *
 * <p>挂 {@code md_supplier_brand.id} 而不是 {@code (supplier_id, brand_name)}: 品牌行由
 * {@code SupplierService#synchronizeBrands} 按名称复用(含恢复软删行), 挂在品牌行上可以让同一品牌被
 * 重新勾选时自动带回既有配置。品牌行软删后配置行保留但不再对外可见(接口按未删除品牌读取)。</p>
 *
 * <p>{@code created_at} 在库侧是带时区的 {@code timestamptz} 且由默认值填充, 故用
 * {@link OffsetDateTime} 只读映射; {@code deleted_flag} 为软删标记, 整集替换时旧行只置位不物理删除。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "md_supplier_brand_spec")
public class SupplierBrandSpec {

    @Id
    private Long id;

    @Column(name = "supplier_brand_id", nullable = false)
    private Long supplierBrandId;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "material", nullable = false, length = 32)
    private String material;

    @Column(name = "spec", nullable = false)
    private Integer spec;

    @Column(name = "length", nullable = false, length = 16)
    private String length;

    @Column(name = "deleted_flag", nullable = false)
    private boolean deletedFlag;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
