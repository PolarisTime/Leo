package com.leo.erp.purchase.order.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "po_purchase_order_item_piece_weight")
public class PurchaseOrderItemPieceWeight {

    @Id
    private Long id;

    @Column(name = "purchase_order_item_id", nullable = false)
    private Long purchaseOrderItemId;

    @Column(name = "piece_no", nullable = false)
    private Integer pieceNo;

    @Column(name = "weight_ton", nullable = false, precision = 14, scale = 3)
    private BigDecimal weightTon;

    @Column(name = "sales_order_item_id")
    private Long salesOrderItemId;
}
