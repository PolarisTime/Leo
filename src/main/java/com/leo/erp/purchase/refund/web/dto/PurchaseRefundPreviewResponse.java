package com.leo.erp.purchase.refund.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record PurchaseRefundPreviewResponse(
        Long sourcePurchaseOrderId,
        String purchaseOrderNo,
        String supplierCode,
        String supplierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        Integer totalQuantity,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        List<PurchaseRefundItemResponse> items
) {
}
