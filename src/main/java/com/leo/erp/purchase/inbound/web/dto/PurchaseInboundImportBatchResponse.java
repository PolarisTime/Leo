package com.leo.erp.purchase.inbound.web.dto;

import java.util.List;

public record PurchaseInboundImportBatchResponse(
        Long id,
        String batchNo,
        Long sourcePurchaseOrderId,
        String sourcePurchaseOrderNo,
        List<InboundDraft> inbounds
) {
    public record InboundDraft(
            Long id,
            String inboundNo,
            Long warehouseId,
            String warehouseName,
            String settlementMode,
            int itemCount,
            String status
    ) {
    }
}
