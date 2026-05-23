package com.leo.erp.purchase.order.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PurchaseOrderResponse(
        Long id,
        String orderNo,
        String supplierName,
        LocalDateTime orderDate,
        String buyerName,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        String remark,
        List<PurchaseOrderItemResponse> items
) {
}
