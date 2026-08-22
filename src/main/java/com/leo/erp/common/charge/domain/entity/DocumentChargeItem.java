package com.leo.erp.common.charge.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 通用单据附加费用行：采购订单/销售订单/物流单共用，
 * 独立于各 items 货物明细表，避免污染重量、件数与下游导入数学。
 */
@Getter
@Setter
@Entity
@Table(name = "bd_document_charge_item")
public class DocumentChargeItem extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "module_key", nullable = false, length = 64)
    private String moduleKey;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    /** 行号由服务层同步时按当前有效费用行重编号。 */
    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "charge_name", nullable = false, length = 128)
    private String chargeName;

    /** 关联费用主数据（md_material 中 material_type=附加费用 的行），可空。 */
    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "unit", length = 16)
    private String unit;

    @Column(name = "remark", length = 255)
    private String remark;
}
