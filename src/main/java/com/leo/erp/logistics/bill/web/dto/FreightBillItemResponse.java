package com.leo.erp.logistics.bill.web.dto;

import java.math.BigDecimal;

public record FreightBillItemResponse(
        Long id,
        Integer lineNo,
        String sourceNo,
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
        String batchNoNormalized,
        BigDecimal weightTon,
        Long warehouseId,
        String warehouseName,
        Long sourceFreightBillId,
        Long sourceFreightBillItemId,
        Long sourceSalesOrderItemId
) {
}
