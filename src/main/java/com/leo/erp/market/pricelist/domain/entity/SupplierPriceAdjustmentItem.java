package com.leo.erp.market.pricelist.domain.entity;

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

/** 价格表加减留痕明细: 记录每个条目的调整前价与调整后价。 */
@Getter
@Setter
@Entity
@Table(name = "mk_supplier_price_adjustment_item")
public class SupplierPriceAdjustmentItem {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjustment_id", nullable = false)
    private SupplierPriceAdjustment adjustment;

    /** 被调整的条目ID(不加外键, 保留留痕)。 */
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "price_before", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceBefore;

    @Column(name = "price_after", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAfter;
}
