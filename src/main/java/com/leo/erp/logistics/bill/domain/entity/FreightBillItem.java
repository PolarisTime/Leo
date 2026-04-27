package com.leo.erp.logistics.bill.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "lg_freight_bill_item")
public class FreightBillItem {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private FreightBill freightBill;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "source_no", nullable = false, length = 64)
    private String sourceNo;

    @Column(name = "customer_name", nullable = false, length = 128)
    private String customerName;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @Column(name = "material_code", nullable = false, length = 64)
    private String materialCode;

    @Column(name = "material_name", length = 128)
    private String materialName;

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

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "quantity_unit", nullable = false, length = 16)
    private String quantityUnit;

    @Column(name = "piece_weight_ton", nullable = false, precision = 12, scale = 3)
    private BigDecimal pieceWeightTon;

    @Column(name = "pieces_per_bundle", nullable = false)
    private Integer piecesPerBundle;

    @Column(name = "batch_no", length = 64)
    private String batchNo;

    @Column(name = "weight_ton", nullable = false, precision = 14, scale = 3)
    private BigDecimal weightTon;

    @Column(name = "warehouse_name", length = 128)
    private String warehouseName;
}
