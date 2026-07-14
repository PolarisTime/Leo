package com.leo.erp.finance.purchaseflow.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseFinanceFlowLineResponse(
        long flowSequence,
        LocalDate businessDate,
        String documentRole,
        String documentType,
        Long documentId,
        String documentNo,
        Long documentItemId,
        Integer lineNo,
        String sourceDocumentType,
        Long sourceDocumentId,
        String sourceDocumentNo,
        Long sourceDocumentItemId,
        Integer sourceLineNo,
        Long rootPurchaseOrderId,
        Long rootPurchaseOrderItemId,
        Long settlementCompanyId,
        String settlementCompanyName,
        Long supplierId,
        String supplierCode,
        String supplierName,
        Long materialId,
        String materialCode,
        String materialName,
        Integer quantity,
        String quantityUnit,
        BigDecimal actualWeightTon,
        BigDecimal unitPrice,
        BigDecimal lineAmount,
        BigDecimal expenseAmount,
        BigDecimal incomeAmount,
        String adjustmentDirection,
        String adjustmentEffect,
        String status,
        boolean effective,
        String remark
) {
}
