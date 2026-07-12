package com.leo.erp.statement.supplier.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierStatementCandidateResponse(
        Long id,
        String inboundNo,
        String supplierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        String warehouseName,
        LocalDate inboundDate,
        String settlementMode,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        Long supplierId,
        Long warehouseId
) {
    public SupplierStatementCandidateResponse(Long id,
                                              String inboundNo,
                                              String supplierName,
                                              Long settlementCompanyId,
                                              String settlementCompanyName,
                                              String warehouseName,
                                              LocalDate inboundDate,
                                              String settlementMode,
                                              BigDecimal totalWeight,
                                              BigDecimal totalAmount,
                                              String status) {
        this(id, inboundNo, supplierName, settlementCompanyId, settlementCompanyName, warehouseName, inboundDate,
                settlementMode, totalWeight, totalAmount, status, null, null);
    }

    public SupplierStatementCandidateResponse(Long id,
                                              String inboundNo,
                                              String supplierName,
                                              String warehouseName,
                                              LocalDate inboundDate,
                                              String settlementMode,
                                              BigDecimal totalWeight,
                                              BigDecimal totalAmount,
                                              String status) {
        this(id, inboundNo, supplierName, null, null, warehouseName, inboundDate, settlementMode,
                totalWeight, totalAmount, status, null, null);
    }
}
