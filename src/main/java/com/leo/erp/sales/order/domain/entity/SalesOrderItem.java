package com.leo.erp.sales.order.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "so_sales_order_item")
public class SalesOrderItem {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private SalesOrder salesOrder;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "material_code", nullable = false, length = 64)
    private String materialCode;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "brand", nullable = false, length = 64)
    private String brand;

    @Column(name = "category", nullable = false, length = 16)
    private String category;

    @Column(name = "material", nullable = false, length = 16)
    private String material;

    @Column(name = "spec", nullable = false, length = 64)
    private String spec;

    @Column(name = "length", length = 32)
    private String length;

    @Column(name = "unit", nullable = false, length = 8)
    private String unit;

    @Column(name = "source_inbound_item_id")
    private Long sourceInboundItemId;

    /**
     * 采购订单直连来源明细：<b>仍在服役的兼容字段，不是只读历史</b>。
     *
     * <p>销售订单明细的「采购订单直连来源」口径（历史单据）仍允许在新建/更新时写入
     * （{@code SalesOrderItemMapper} 从请求复制；来源形态由
     * {@code SalesOrderSourceAllocationService#validateSourceShape} 收敛为「采购入库明细」或
     * 「采购订单明细」二选一）。因此列注释里的「仅保留无法无损映射的历史」只描述来源数据分布，
     * 不代表写入已停止；删除该列需先产品确认下线「采购订单直连来源」能力。</p>
     */
    @Column(name = "source_purchase_order_item_id")
    private Long sourcePurchaseOrderItemId;

    @Column(name = "settlement_company_id")
    private Long settlementCompanyId;

    @Column(name = "settlement_company_name", length = 128)
    private String settlementCompanyName;

    @Column(name = "warehouse_name", length = 128)
    private String warehouseName;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "batch_no", length = 64)
    private String batchNo;

    @Column(name = "batch_no_normalized", insertable = false, updatable = false, length = 64)
    private String batchNoNormalized;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "quantity_unit", nullable = false, length = 8)
    private String quantityUnit;

    @Column(name = "piece_weight_ton", nullable = false, precision = 18, scale = 8)
    private BigDecimal pieceWeightTon;

    @Column(name = "pieces_per_bundle", nullable = false)
    private Integer piecesPerBundle;

    @Column(name = "weight_ton", nullable = false, precision = 18, scale = 8)
    private BigDecimal weightTon;

    @Column(name = "original_weight_ton", precision = 18, scale = 8)
    private BigDecimal originalWeightTon;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
}
