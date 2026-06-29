package com.leo.erp.logistics.bill.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FreightBillImportCandidateResponse(
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
        String status
) {
    public FreightBillImportCandidateResponse(
            Long id,
            String outboundNo,
            String salesOrderNo,
            String customerName,
            String projectName,
            String warehouseName,
            LocalDate outboundDate,
            BigDecimal totalWeight,
            BigDecimal totalAmount,
            String status
    ) {
        this(id, outboundNo, salesOrderNo, customerName, projectName, warehouseName, null, null,
                outboundDate, totalWeight, totalAmount, status);
    }
}
