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
        LocalDate outboundDate,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status
) {
}
