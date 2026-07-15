package com.leo.erp.logistics.bill.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record FreightBillItemRequest(
        Long id,
        @NotBlank String sourceNo,
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
        @NotNull @Min(1) Integer quantity,
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
}
