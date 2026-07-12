package com.leo.erp.statement.freight.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FreightStatementCandidateResponse(
        Long id,
        String billNo,
        String carrierCode,
        String carrierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        String customerName,
        String projectName,
        LocalDate billTime,
        BigDecimal totalWeight,
        BigDecimal totalFreight,
        String status,
        Long carrierId
) {
    public FreightStatementCandidateResponse(Long id,
                                             String billNo,
                                             String carrierCode,
                                             String carrierName,
                                             Long settlementCompanyId,
                                             String settlementCompanyName,
                                             String customerName,
                                             String projectName,
                                             LocalDate billTime,
                                             BigDecimal totalWeight,
                                             BigDecimal totalFreight,
                                             String status) {
        this(id, billNo, carrierCode, carrierName, settlementCompanyId, settlementCompanyName, customerName,
                projectName, billTime, totalWeight, totalFreight, status, null);
    }

    public FreightStatementCandidateResponse(Long id,
                                             String billNo,
                                             String carrierName,
                                             String customerName,
                                             String projectName,
                                             LocalDate billTime,
                                             BigDecimal totalWeight,
                                             BigDecimal totalFreight,
                                             String status) {
        this(id, billNo, null, carrierName, null, null, customerName, projectName, billTime,
                totalWeight, totalFreight, status, null);
    }
}
