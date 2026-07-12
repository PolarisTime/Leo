package com.leo.erp.purchase.refund.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record PurchaseRefundPreviewResponse(
        Long sourcePurchaseOrderId,
        String purchaseOrderNo,
        Long supplierId,
        String supplierCode,
        String supplierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        Integer totalQuantity,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        List<PurchaseRefundItemResponse> items
) {
    public PurchaseRefundPreviewResponse(Long sourcePurchaseOrderId,
                                         String purchaseOrderNo,
                                         String supplierCode,
                                         String supplierName,
                                         Long settlementCompanyId,
                                         String settlementCompanyName,
                                         Integer totalQuantity,
                                         BigDecimal totalWeight,
                                         BigDecimal totalAmount,
                                         List<PurchaseRefundItemResponse> items) {
        this(sourcePurchaseOrderId, purchaseOrderNo, null, supplierCode, supplierName,
                settlementCompanyId, settlementCompanyName, totalQuantity, totalWeight, totalAmount, items);
    }
}
