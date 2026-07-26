package com.leo.erp.sales.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOrderSourceSnapshot(
        Long id,
        String orderNo,
        String purchaseInboundNo,
        String purchaseOrderNo,
        String customerCode,
        Long customerId,
        String customerName,
        Long projectId,
        String projectName,
        Long settlementCompanyId,
        String settlementCompanyName,
        LocalDate deliveryDate,
        String salesName,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        boolean deletedFlag,
        String remark,
        List<SalesOrderSourceItemSnapshot> items
) {
}
