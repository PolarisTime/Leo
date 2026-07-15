package com.leo.erp.sales.outbound.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOutboundResponse(
        Long id,
        String outboundNo,
        String salesOrderNo,
        Long customerId,
        String customerName,
        Long projectId,
        String projectName,
        Long warehouseId,
        String warehouseName,
        Long settlementCompanyId,
        String settlementCompanyName,
        LocalDate outboundDate,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        boolean deletedFlag,
        String remark,
        List<SalesOutboundItemResponse> items
) {
}
