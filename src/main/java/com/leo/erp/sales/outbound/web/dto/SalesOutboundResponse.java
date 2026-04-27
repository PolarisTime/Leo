package com.leo.erp.sales.outbound.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOutboundResponse(
        Long id,
        String outboundNo,
        String salesOrderNo,
        String customerName,
        String projectName,
        String warehouseName,
        LocalDate outboundDate,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        String remark,
        List<SalesOutboundItemResponse> items
) {
}
