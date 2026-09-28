package com.leo.erp.market.pricelist.domain.entity;

import com.leo.erp.market.pricelist.domain.enums.PriceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 供应商价格表条目。
 * <p>{@code price == null} 表示不报价(与 0 元严格区分); 长度已从材质名拆出, 无定尺概念时存空串。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "mk_supplier_price_item")
public class SupplierPriceItem {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_id", nullable = false)
    private SupplierPriceList list;

    @Column(name = "category", nullable = false, length = 16)
    private String category;

    @Column(name = "material", nullable = false, length = 16)
    private String material;

    /** 规格 = 直径 mm, 必须大于 0。 */
    @Column(name = "spec", nullable = false)
    private Integer spec;

    @Column(name = "length", nullable = false, length = 16)
    private String length;

    /** 单价(元/吨, 含税出厂价); null = 不报价。 */
    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_status", nullable = false, length = 16)
    private PriceStatus priceStatus = PriceStatus.NORMAL;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** 条目业务键(与单据行匹配): 类别 + 材质 + 规格 + 定尺。 */
    public String keyOf() {
        return key(category, material, spec, length);
    }

    /** 业务键文本形式, 供重复校验与错误明细展示。 */
    public static String key(String category, String material, Integer spec, String length) {
        return (category == null ? "" : category) + "|"
                + (material == null ? "" : material) + "|"
                + (spec == null ? "" : spec) + "|"
                + (length == null ? "" : length);
    }
}
