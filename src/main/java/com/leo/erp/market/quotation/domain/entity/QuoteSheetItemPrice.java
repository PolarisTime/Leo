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
import java.time.LocalDateTime;

/**
 * 比价单行×品牌现货价的历史落库行。
 *
 * <p><b>已不再被读/写</b>(契约修订: 删除手填覆盖): 现货价完全由供应商价格表在读取时推导,
 * 本表仅保留历史数据与 {@code mk_quote_item_price} 的既有列, 不参与任何取价逻辑。</p>
 *
 * <p><b>历史价格快照能力随之移除</b>(原 {@code POST /quote-sheets/{id}/price-pulls} 固化入口已删除):
 * 若日后需要按单据冻结历史价, 必须重新引入快照写入点(而不是恢复本表读路径), 否则会把"当前价"
 * 误当"成交快照"使用。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "mk_quote_item_price")
public class QuoteSheetItemPrice {

    /** 价格来源历史值: 单据手填/覆盖(仅历史数据, 不再写入)。 */
    public static final String SOURCE_MANUAL = "MANUAL";
    /** 价格来源历史值: 由供应商价格表推导或固化(仅历史数据, 不再写入)。 */
    public static final String SOURCE_PRICE_LIST = "PRICE_LIST";

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

    /** 价格来源: {@link #SOURCE_MANUAL} 手填覆盖 / {@link #SOURCE_PRICE_LIST} 价格表。 */
    @Column(name = "price_source", nullable = false, length = 16)
    private String priceSource = SOURCE_MANUAL;

    /** 来源价格表ID快照(历史值, 不建外键)。 */
    @Column(name = "price_list_id")
    private Long priceListId;

    /** 来源价格表时间快照(历史值; 已取消版本语义, 现为价格表 updated_at)。 */
    @Column(name = "price_list_released_at")
    private LocalDateTime priceListReleasedAt;

    public boolean isManual() {
        return !SOURCE_PRICE_LIST.equals(priceSource);
    }
}
