package com.leo.erp.market.pricelist.domain.entity;

import com.leo.erp.market.pricelist.domain.enums.PriceAdjustmentMode;
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
import java.time.LocalDateTime;

/** 价格表整表/选区加减留痕头(只追加, 不可修改)。 */
@Getter
@Setter
@Entity
@Table(name = "mk_supplier_price_adjustment")
public class SupplierPriceAdjustment {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_id", nullable = false)
    private SupplierPriceList list;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 8)
    private PriceAdjustmentMode mode;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "item_count", nullable = false)
    private Integer itemCount = 0;

    @Column(name = "created_by", nullable = false)
    private Long createdBy = 0L;

    @Column(name = "created_name", nullable = false, length = 64)
    private String createdName = "system";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
