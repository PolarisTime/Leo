package com.leo.erp.logistics.bill.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record FreightBillItemRequest(
        Long id,
        @NotBlank String sourceNo,
        @NotNull @Positive Long sourceSalesOutboundItemId,
        Long settlementCompanyId,
        String settlementCompanyName,
        Long customerId,
        @NotBlank String customerName,
        Long projectId,
        @NotBlank String projectName,
        Long materialId,
        @NotBlank String materialCode,
        String materialName,
        @NotBlank String brand,
        @NotBlank String category,
        @NotBlank String material,
        @NotBlank String spec,
        String length,
        @NotNull @Min(0) Integer quantity,
        String quantityUnit,
        @NotNull @DecimalMin("0.000") BigDecimal pieceWeightTon,
        @NotNull @Min(0) Integer piecesPerBundle,
        String batchNo,
        BigDecimal weightTon,
        Long warehouseId,
        String warehouseName,
        Long sourceFreightBillId,
        Long sourceFreightBillItemId
) {
    public FreightBillItemRequest(Long id,
                                  String sourceNo,
                                  Long sourceSalesOutboundItemId,
                                  Long settlementCompanyId,
                                  String settlementCompanyName,
                                  Long customerId,
                                  String customerName,
                                  Long projectId,
                                  String projectName,
                                  Long materialId,
                                  String materialCode,
                                  String materialName,
                                  String brand,
                                  String category,
                                  String material,
                                  String spec,
                                  String length,
                                  Integer quantity,
                                  String quantityUnit,
                                  BigDecimal pieceWeightTon,
                                  Integer piecesPerBundle,
                                  String batchNo,
                                  BigDecimal weightTon,
                                  Long warehouseId,
                                  String warehouseName) {
        this(id, sourceNo, sourceSalesOutboundItemId, settlementCompanyId, settlementCompanyName, customerId,
                customerName, projectId, projectName, materialId, materialCode, materialName, brand, category,
                material, spec, length, quantity, quantityUnit, pieceWeightTon, piecesPerBundle, batchNo, weightTon,
                warehouseId, warehouseName, null, null);
    }

    public FreightBillItemRequest(Long id,
                                  String sourceNo,
                                  Long sourceSalesOutboundItemId,
                                  Long settlementCompanyId,
                                  String settlementCompanyName,
                                  String customerName,
                                  String projectName,
                                  String materialCode,
                                  String materialName,
                                  String brand,
                                  String category,
                                  String material,
                                  String spec,
                                  String length,
                                  Integer quantity,
                                  String quantityUnit,
                                  BigDecimal pieceWeightTon,
                                  Integer piecesPerBundle,
                                  String batchNo,
                                  BigDecimal weightTon,
                                  String warehouseName) {
        this(id, sourceNo, sourceSalesOutboundItemId, settlementCompanyId, settlementCompanyName, null,
                customerName, null, projectName, null, materialCode, materialName, brand, category, material, spec,
                length, quantity, quantityUnit, pieceWeightTon, piecesPerBundle, batchNo, weightTon, null,
                warehouseName, null, null);
    }

    public FreightBillItemRequest(String sourceNo,
                                  String customerName,
                                  String projectName,
                                  String materialCode,
                                  String materialName,
                                  String brand,
                                  String category,
                                  String material,
                                  String spec,
                                  String length,
                                  Integer quantity,
                                  String quantityUnit,
                                  BigDecimal pieceWeightTon,
                                  Integer piecesPerBundle,
                                  String batchNo,
                                  BigDecimal weightTon,
                                  String warehouseName) {
        this(null, sourceNo, null, null, null, null, customerName, null, projectName, null, materialCode,
                materialName, brand, category, material, spec, length, quantity, quantityUnit, pieceWeightTon,
                piecesPerBundle, batchNo, weightTon, null, warehouseName, null, null);
    }

    public FreightBillItemRequest(Long id,
                                  String sourceNo,
                                  String customerName,
                                  String projectName,
                                  String materialCode,
                                  String materialName,
                                  String brand,
                                  String category,
                                  String material,
                                  String spec,
                                  String length,
                                  Integer quantity,
                                  String quantityUnit,
                                  BigDecimal pieceWeightTon,
                                  Integer piecesPerBundle,
                                  String batchNo,
                                  BigDecimal weightTon,
                                  String warehouseName) {
        this(id, sourceNo, null, null, null, null, customerName, null, projectName, null, materialCode,
                materialName, brand, category, material, spec, length, quantity, quantityUnit,
                pieceWeightTon, piecesPerBundle, batchNo, weightTon, null, warehouseName, null, null);
    }

    public FreightBillItemRequest(Long id,
                                  String sourceNo,
                                  Long sourceSalesOutboundItemId,
                                  String customerName,
                                  String projectName,
                                  String materialCode,
                                  String materialName,
                                  String brand,
                                  String category,
                                  String material,
                                  String spec,
                                  String length,
                                  Integer quantity,
                                  String quantityUnit,
                                  BigDecimal pieceWeightTon,
                                  Integer piecesPerBundle,
                                  String batchNo,
                                  BigDecimal weightTon,
                                  String warehouseName) {
        this(id, sourceNo, sourceSalesOutboundItemId, null, null, null, customerName, null, projectName, null,
                materialCode, materialName, brand, category, material, spec, length, quantity, quantityUnit,
                pieceWeightTon, piecesPerBundle, batchNo, weightTon, null, warehouseName, null, null);
    }
}
