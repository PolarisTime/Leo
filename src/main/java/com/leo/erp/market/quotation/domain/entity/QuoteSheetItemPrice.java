package com.leo.erp.market.quotation.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "mk_quote_item_price")
public class QuoteSheetItemPrice {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private QuoteSheetItem item;

    @Column(name = "brand_name", nullable = false, length = 64)
    private String brandName;

    @Column(name = "spot_price", precision = 12, scale = 2)
    private BigDecimal spotPrice;

    /** 现货价来源供应商ID(主数据)。 */
    @Column(name = "supplier_id")
    private Long supplierId;

    /** 供应商名称快照。 */
    @Column(name = "supplier_name", length = 200)
    private String supplierName;

    /**
     * 该现货价**实际取自**的材质（如行材质 HRB400 用 HRB400E 的价来代取）；为空表示就是本行材质的价。
     *
     * <p>用于追溯「抗震牌号代取」：代取价与精确填写的价在数值上无法区分，但业务含义不同
     * （代取是按替代牌号的行情结算）。前端据此打角标，事后也能查清这一格价从哪来。</p>
     */
    @Column(name = "source_material", length = 16)
    private String sourceMaterial;
}
