package com.leo.erp.finance.invoicereceipt.domain.entity;

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
@Table(name = "fm_invoice_receipt_item")
public class InvoiceReceiptItem {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private InvoiceReceipt invoiceReceipt;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "source_no", nullable = false, length = 64)
    private String sourceNo;

    @Column(name = "source_purchase_order_item_id")
    private Long sourcePurchaseOrderItemId;

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
