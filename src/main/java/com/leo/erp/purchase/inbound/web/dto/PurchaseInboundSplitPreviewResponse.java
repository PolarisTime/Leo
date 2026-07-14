package com.leo.erp.purchase.inbound.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record PurchaseInboundSplitPreviewResponse(
        Long sourcePurchaseOrderId,
        String sourcePurchaseOrderNo,
        Long supplierId,
        String supplierCode,
        String supplierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        boolean importAllowed,
        String blockingReason,
        int expectedDraftCount,
        List<Group> groups
) {
    public record Group(
            Long warehouseId,
            String warehouseName,
            String settlementMode,
            int totalQuantity,
            BigDecimal totalTheoreticalWeightTon,
            BigDecimal totalAmount,
            List<Item> items
    ) {
    }

    public record Item(
            Long sourcePurchaseOrderItemId,
            Integer sourceLineNo,
            Long materialId,
            String materialCode,
            String brand,
            String category,
            String material,
            String spec,
            String length,
            String unit,
            String batchNo,
            int remainingQuantity,
            String quantityUnit,
            BigDecimal pieceWeightTon,
            Integer piecesPerBundle,
            BigDecimal theoreticalWeightTon,
            BigDecimal unitPrice,
            BigDecimal amount
    ) {
    }
}
