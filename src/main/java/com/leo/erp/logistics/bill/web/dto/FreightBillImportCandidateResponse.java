package com.leo.erp.logistics.bill.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FreightBillImportCandidateResponse(
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
        String status
) {
    public FreightBillImportCandidateResponse(Long id,
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
                                              String status) {
        this(id, outboundNo, salesOrderNo, null, customerName, null, projectName, null, warehouseName,
                settlementCompanyId, settlementCompanyName, outboundDate, totalWeight, totalAmount, status);
    }

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
        this(id, outboundNo, salesOrderNo, null, customerName, null, projectName, null, warehouseName, null, null,
                outboundDate, totalWeight, totalAmount, status);
    }
}
