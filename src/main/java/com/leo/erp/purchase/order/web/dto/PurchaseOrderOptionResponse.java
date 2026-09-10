package com.leo.erp.purchase.order.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 采购订单下拉选项(供报价单关联/展示订货吨数)。 */
public record PurchaseOrderOptionResponse(
        Long id,
        String orderNo,
        String supplierName,
        BigDecimal totalWeight,
        String status,
        LocalDateTime orderDate
) {
}
