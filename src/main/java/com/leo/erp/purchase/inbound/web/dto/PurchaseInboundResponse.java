package com.leo.erp.purchase.inbound.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseInboundResponse(
        Long id,
        String inboundNo,
        String purchaseOrderNo,
        String supplierName,
        String warehouseName,
        LocalDate inboundDate,
        String settlementMode,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        String remark,
        List<PurchaseInboundItemResponse> items
) {
}
