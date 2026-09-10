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
}
