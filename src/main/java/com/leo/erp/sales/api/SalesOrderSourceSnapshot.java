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
        /** 报单日期: 前端销售订单形状必填, 物流单候选/导入候选都必须回传。 */
        LocalDate orderDate,
        String salesName,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        boolean deletedFlag,
        String remark,
        List<SalesOrderSourceItemSnapshot> items
) {
}
