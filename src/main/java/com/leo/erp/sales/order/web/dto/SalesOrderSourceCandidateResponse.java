package com.leo.erp.sales.order.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOrderSourceCandidateResponse(
        Long id,
        String orderNo,
        String purchaseOrderNo,
        String sourceDocumentType,
        String sourceNo,
        Long supplierId,
        String supplierCode,
        String supplierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        LocalDate orderDate,
        String status,
        Integer importableQuantity,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        List<SalesOrderSourceCandidateItemResponse> items
) {
}
