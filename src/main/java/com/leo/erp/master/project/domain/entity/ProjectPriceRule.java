package com.leo.erp.master.project.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 项目价格规定(网价浮动规则): 名称 + 方向 + 金额 + 备注。 */
@Getter
@Setter
@Entity
@Table(name = "md_project_price_rule")
public class ProjectPriceRule extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 规定名称(项目内唯一)。 */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 方向: ADD加价/SUBTRACT减价。 */
    @Column(name = "mode", nullable = false, length = 8)
    private String mode;

    /** 固定幅度(元/吨), 非负。 */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
