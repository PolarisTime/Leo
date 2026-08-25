package com.leo.erp.system.printtemplate.service;

public record PrintRecordItem(
        String id,
        String recordId,
        String brand,
        String category,
        String settlementMode,
        String material,
        String spec,
        String length,
        String quantity,
        String pieceWeightTon,
        String weightTon,
        String unitPrice,
        String amount,
        String sourceNo,
        String deliveryDate,
        String quantityUnit,
        String customerName,
        String projectName,
        String sourceSalesOrderItemId,
        String sourceFreightBillId,
        String sourceFreightBillUnitPrice,
        String sourceFreightBillTotalFreight
) {
    public PrintRecordItem(
            String id,
            String recordId,
            String brand,
            String category,
            String material,
            String spec,
            String quantity,
            String pieceWeightTon,
            String weightTon,
            String unitPrice,
            String amount
    ) {
        this(id, recordId, brand, category, "", material, spec, "", quantity, pieceWeightTon, weightTon, unitPrice, amount,
                "", "", "", "", "", "", "", "", "");
    }
}
