package com.leo.erp.master.valuealias.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 值映射/别名（{@code md_value_alias}）。
 *
 * <p>把类别(CATEGORY)/材质(MATERIAL)/定尺(LENGTH)/品牌(BRAND) 四个维度的「同一含义多种写法」
 * 归一规则从硬编码改为可维护的表数据。只做同义归一，不做跨语义合并——例如「直条」与「螺纹钢」
 * 是同一类别的两种写法（可归一），而「螺纹钢」与「盘螺」是不同类别，严禁互相映射。</p>
 *
 * <p>两条不变量由数据库约束兜底：自映射（源写法等于目标写法）被 {@code ck_value_alias_self_mapping}
 * 拒绝；同维度内同一源写法只允许一条未删除记录，由部分唯一索引
 * {@code uk_value_alias_dimension_source} 保证（软删后可重建同源写法）。</p>
 */
@Entity
@Table(name = "md_value_alias")
public class MdValueAlias extends AbstractAuditableEntity {

    @Id
    private Long id;

    /** 维度：CATEGORY / MATERIAL / LENGTH / BRAND。 */
    @Column(name = "dimension", nullable = false, length = 16)
    private String dimension;

    /** 源写法（用户录入或历史数据里的写法）；同维度内未删除记录唯一。 */
    @Column(name = "source_value", nullable = false, length = 64)
    private String sourceValue;

    /** 目标写法（归一后的规范写法），必须与源写法不同。 */
    @Column(name = "target_value", nullable = false, length = 64)
    private String targetValue;

    /** 备注，说明该别名的来源。 */
    @Column(name = "remark", length = 255)
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public String getSourceValue() {
        return sourceValue;
    }

    public void setSourceValue(String sourceValue) {
        this.sourceValue = sourceValue;
    }

    public String getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(String targetValue) {
        this.targetValue = targetValue;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
