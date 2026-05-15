package com.leo.erp.finance.projectar.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProjectArSummaryResponse(
        Long projectId,
        String customerCode,
        String customerName,
        String projectName,
        String projectNameAbbr,
        String projectManager,
        BigDecimal completedSalesAmount,
        BigDecimal receivedAmount,
        BigDecimal unreceivedAmount,
        BigDecimal prepaymentBalance,
        BigDecimal netUnreceivedAmount,
        Integer unreconciledDocumentCount,
        Integer reconciledDocumentCount,
        LocalDate latestBusinessDate
) {
}
