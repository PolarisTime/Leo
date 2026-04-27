package com.leo.erp.purchase.order.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseOrderResponse(
        Long id,
        String orderNo,
        String supplierName,
        LocalDate orderDate,
        String buyerName,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        String remark,
        List<PurchaseOrderItemResponse> items
) {
}
