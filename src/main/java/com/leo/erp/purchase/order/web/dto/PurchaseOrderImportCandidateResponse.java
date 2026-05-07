package com.leo.erp.purchase.order.web.dto;

import java.time.LocalDate;

public record PurchaseOrderImportCandidateResponse(
        Long id,
        String orderNo,
        String supplierName,
        String buyerName,
        LocalDate orderDate,
        String status,
        Integer importableQuantity
) {
}
