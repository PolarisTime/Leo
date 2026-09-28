package com.leo.erp.market.pricelist.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import com.leo.erp.market.pricelist.domain.enums.ValueAliasDimension;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 值映射/别名行({@code md_value_alias}): 把某维度下的一种写法归一到另一种写法。
 *
 * <p>语义边界: <b>只做同一含义多写法归一, 不做跨语义合并</b>; 源值与目标值相同(自映射)由数据库
 * CHECK 与应用层双重拒绝; 同一 (维度, 源值) 仅一条未删除记录, 软删后可重建同源值。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "md_value_alias")
public class ValueAlias extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "dimension", nullable = false, length = 16)
    private ValueAliasDimension dimension;

    @Column(name = "source_value", nullable = false, length = 64)
    private String sourceValue;

    @Column(name = "target_value", nullable = false, length = 64)
    private String targetValue;

    @Column(name = "remark", length = 255)
    private String remark;
}
