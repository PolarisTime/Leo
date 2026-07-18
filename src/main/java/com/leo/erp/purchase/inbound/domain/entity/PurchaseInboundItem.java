package com.leo.erp.purchase.inbound.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "po_purchase_inbound_item")
public class PurchaseInboundItem {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inbound_id", nullable = false)
    private PurchaseInbound purchaseInbound;

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

    @Column(name = "settlement_mode", nullable = false, length = 32)
    private String settlementMode;

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

    @Column(name = "weigh_weight_ton", precision = 18, scale = 8)
    private BigDecimal weighWeightTon;

    @Column(name = "weight_adjustment_ton", nullable = false, precision = 18, scale = 8)
    private BigDecimal weightAdjustmentTon = BigDecimal.ZERO;

    @Column(name = "weight_adjustment_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal weightAdjustmentAmount = BigDecimal.ZERO;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

}
