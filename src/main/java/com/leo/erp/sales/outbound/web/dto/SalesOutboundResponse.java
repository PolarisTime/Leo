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
        Long settlementCompanyId,
        String settlementCompanyName,
        LocalDate outboundDate,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        String remark,
        List<SalesOutboundItemResponse> items
) {
    public SalesOutboundResponse(Long id,
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
                                 List<SalesOutboundItemResponse> items) {
        this(id, outboundNo, salesOrderNo, customerName, projectName, warehouseName, null, null,
                outboundDate, totalWeight, totalAmount, status, remark, items);
    }
}
