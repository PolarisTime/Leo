package com.leo.erp.purchase.order.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PurchaseOrderImportCandidateResponse(
        Long id,
        String orderNo,
        String supplierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        String buyerName,
        LocalDateTime orderDate,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        Integer importableQuantity
) {
    public PurchaseOrderImportCandidateResponse(
            Long id,
            String orderNo,
            String supplierName,
            String buyerName,
            LocalDateTime orderDate,
            String status,
            Integer importableQuantity
    ) {
        this(id, orderNo, supplierName, null, null, buyerName, orderDate, null, null, status, importableQuantity);
    }
}
