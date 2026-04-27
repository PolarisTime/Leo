package com.leo.erp.master.material.domain.entity;

import com.leo.erp.common.persistence.AuditableEntity;
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
@Table(name = "md_material")
public class Material extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "material_code", nullable = false, unique = true, length = 64)
    private String materialCode;

    @Column(name = "brand", nullable = false, length = 64)
    private String brand;

    @Column(name = "material", nullable = false, length = 32)
    private String material;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "spec", nullable = false, length = 32)
    private String spec;

    @Column(name = "length", length = 32)
    private String length;

    @Column(name = "unit", nullable = false, length = 16)
    private String unit;

    @Column(name = "quantity_unit", nullable = false, length = 16)
    private String quantityUnit;

    @Column(name = "piece_weight_ton", nullable = false, precision = 12, scale = 3)
    private BigDecimal pieceWeightTon;

    @Column(name = "pieces_per_bundle", nullable = false)
    private Integer piecesPerBundle;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "batch_no_enabled", nullable = false)
    private Boolean batchNoEnabled = Boolean.FALSE;

    @Column(name = "remark", length = 255)
    private String remark;
}
