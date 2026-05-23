package com.leo.erp.purchase.order.web.dto;

import java.time.LocalDateTime;

public record PurchaseOrderImportCandidateResponse(
        Long id,
        String orderNo,
        String supplierName,
        String buyerName,
        LocalDateTime orderDate,
        String status,
        Integer importableQuantity
) {
}
