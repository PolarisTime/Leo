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

    @Column(name = "brand", nullable = false, length = 64)
    private String brand;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "material", nullable = false, length = 32)
    private String material;

    @Column(name = "spec", nullable = false, length = 32)
    private String spec;

    @Column(name = "length", length = 32)
    private String length;

    @Column(name = "unit", nullable = false, length = 16)
    private String unit;

    @Column(name = "source_inbound_item_id")
    private Long sourceInboundItemId;

    @Column(name = "warehouse_name", length = 128)
    private String warehouseName;

    @Column(name = "batch_no", length = 64)
    private String batchNo;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "quantity_unit", nullable = false, length = 16)
    private String quantityUnit;

    @Column(name = "piece_weight_ton", nullable = false, precision = 12, scale = 3)
    private BigDecimal pieceWeightTon;

    @Column(name = "pieces_per_bundle", nullable = false)
    private Integer piecesPerBundle;

    @Column(name = "weight_ton", nullable = false, precision = 14, scale = 3)
    private BigDecimal weightTon;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
}
