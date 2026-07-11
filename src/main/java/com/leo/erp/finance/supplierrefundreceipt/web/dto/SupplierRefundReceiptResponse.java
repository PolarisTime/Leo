package com.leo.erp.finance.supplierrefundreceipt.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierRefundReceiptResponse(
        Long id,
        String refundReceiptNo,
        Long purchaseRefundId,
        String supplierCode,
        String supplierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        LocalDate receiptDate,
        String receiptMethod,
        BigDecimal amount,
        String status,
        boolean deletedFlag,
        String operatorName,
        String remark
) {
}
