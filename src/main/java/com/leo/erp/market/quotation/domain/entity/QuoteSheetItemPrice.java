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

@Getter
@Setter
@Entity
@Table(name = "mk_quote_item_price")
public class QuoteSheetItemPrice {

    /** 价格来源: 单据手填/覆盖(含全部历史数据)。 */
    public static final String SOURCE_MANUAL = "MANUAL";
    /** 价格来源: 由供应商价格表推导或显式固化。 */
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

    /** 来源价格表版本ID(快照, 不建外键)。 */
    @Column(name = "price_list_id")
    private Long priceListId;

    /** 来源价格表版本的发布时刻快照。 */
    @Column(name = "price_list_released_at")
    private LocalDateTime priceListReleasedAt;

    public boolean isManual() {
        return !SOURCE_PRICE_LIST.equals(priceSource);
    }
}
