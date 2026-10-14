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

/**
 * 报价单商品行 × 关联的采购订单明细行(一行可关联多张)。
 *
 * <p>计划态引用, 与 {@link QuoteSheetItem#getPurchaseOrderId()} 同口径: <b>刻意不加订单外键</b>
 * —— 订单删除后本行需保留订单号快照与历史关联(理由见 {@code QuoteSheetItem} 对应字段注释)。</p>
 *
 * <p>{@code allocatedTon} 是"本次在该订单明细行开出的吨位": 一行报 130 吨可以拆到多条订单明细行,
 * 各条之和即该行已分配的吨位。默认按关联顺序自动填满, 允许前端手工调整(参照 SAP 分配表 /
 * Oracle firm allocation 的"自动 + 手工"组合)。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "mk_quote_item_purchase_order")
public class QuoteSheetItemPurchaseOrder {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private QuoteSheetItem item;

    /** 关联采购订单标识(计划态引用, 不加外键)。 */
    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    /** 关联采购订单号快照(保存时写入), 保证订单号变更/删除后仍可读。 */
    @Column(name = "purchase_order_no", length = 64)
    private String purchaseOrderNo;

    /** 关联采购订单明细行标识: 按规格扣减已开吨位的真源。 */
    @Column(name = "purchase_order_item_id", nullable = false)
    private Long purchaseOrderItemId;

    /** 本次在该订单明细行开出的吨位。 */
    @Column(name = "allocated_ton", nullable = false, precision = 18, scale = 8)
    private BigDecimal allocatedTon = BigDecimal.ZERO;

    /** 关联顺序(自动分配与展示顺序)。 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;
}
