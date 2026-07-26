package com.leo.erp.purchase.api;

import java.math.BigDecimal;

public record PurchaseOrderPrepaymentSnapshot(
        Long purchaseOrderId,
        String purchaseOrderNo,
        Long supplierId,
        String supplierCode,
        String supplierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        BigDecimal originalAmount
) {
}
