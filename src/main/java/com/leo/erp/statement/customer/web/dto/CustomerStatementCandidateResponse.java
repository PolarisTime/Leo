package com.leo.erp.statement.customer.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CustomerStatementCandidateResponse(
        Long id,
        String orderNo,
        String customerName,
        String projectName,
        Long settlementCompanyId,
        String settlementCompanyName,
        LocalDate deliveryDate,
        String salesName,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status
) {
    public CustomerStatementCandidateResponse(Long id,
                                              String orderNo,
                                              String customerName,
                                              String projectName,
                                              LocalDate deliveryDate,
                                              String salesName,
                                              BigDecimal totalWeight,
                                              BigDecimal totalAmount,
                                              String status) {
        this(id, orderNo, customerName, projectName, null, null, deliveryDate, salesName,
                totalWeight, totalAmount, status);
    }
}
