package com.leo.erp.logistics.bill.web.dto;

import java.math.BigDecimal;

public record FreightBillItemResponse(
        Long id,
        Integer lineNo,
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
        String warehouseName
) {
}
