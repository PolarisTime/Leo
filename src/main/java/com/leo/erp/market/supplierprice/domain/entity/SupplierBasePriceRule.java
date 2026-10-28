package com.leo.erp.market.supplierprice.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 供应商品牌基价加价规则行({@code mk_supplier_base_price_rule}) —— 一套规则按
 * {@code (supplier_id, brand_name)} 归组, 每行描述一个规格相对基价的加减。
 *
 * <p><b>整体替换而非逐行更新</b>: 保存时先软删该套的全部旧行、再插入新行(同一事务), 因此软删行只保留
 * 审计痕迹, 只有 {@code deleted_flag = false} 的行参与唯一性与版本计算。</p>
 *
 * <p><b>{@link #version} 不是 JPA {@code @Version}</b>: 它描述「整套规则」的版本 —— 同一套规则的全部
 * 未删除行共享同一个值, 每次整体替换 +1, 读取时取未删除行的最大值。原因是乐观锁的粒度是整套规则而不是
 * 单行, 用 JPA 的行级 {@code @Version} 会在软删旧行时各自自增, 无法表达「整套被替换了一次」。</p>
 */
@Entity
@Table(name = "mk_supplier_base_price_rule")
@Getter
@Setter
public class SupplierBasePriceRule extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    /** 品牌/产地, 与该供应商价格表的 {@code brand_name} 同口径。 */
    @Column(name = "brand_name", nullable = false, length = 64)
    private String brandName;

    /** 材质; 定尺已拆到 {@link #length}, 不得含「9米」。 */
    @Column(name = "material", nullable = false, length = 16)
    private String material;

    /** 规格 = 直径(mm), 必须大于 0。 */
    @Column(name = "spec", nullable = false)
    private Integer spec;

    /** 定尺; 无定尺概念时存空串。 */
    @Column(name = "length", nullable = false, length = 16)
    private String length;

    /** 相对基价的加减(元/吨), 可为负; 基价行必须为 0。 */
    @Column(name = "delta", nullable = false, precision = 12, scale = 2)
    private BigDecimal delta;

    /** 是否为基价规格; 每个 {@code (supplierId, brandName)} 至多一条。 */
    @Column(name = "is_base", nullable = false)
    private boolean base;

    /** 整套规则的版本号; 见类注释, 不是 JPA 乐观锁列。 */
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
