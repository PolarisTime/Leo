package com.leo.erp.purchase.order.domain.entity;

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
@Table(name = "po_purchase_order_item")
public class PurchaseOrderItem {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

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

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "actual_weight_ton", precision = 18, scale = 8)
    private BigDecimal actualWeightTon;

}
