package com.leo.erp.logistics.bill.web.dto;

import java.math.BigDecimal;

public record FreightBillItemResponse(
        Long id,
        Integer lineNo,
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
        String warehouseName
) {
    public FreightBillItemResponse(Long id,
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
                                   String warehouseName) {
        this(id, lineNo, sourceNo, null, null, null, customerName, projectName, materialCode,
                materialName, brand, category, material, spec, length, quantity, quantityUnit,
                pieceWeightTon, piecesPerBundle, batchNo, weightTon, warehouseName);
    }
}
