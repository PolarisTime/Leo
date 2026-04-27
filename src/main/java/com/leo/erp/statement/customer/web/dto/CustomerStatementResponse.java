package com.leo.erp.statement.customer.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CustomerStatementResponse(
        Long id,
        String statementNo,
        String sourceOrderNos,
        String customerName,
        String projectName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal salesAmount,
        BigDecimal receiptAmount,
        BigDecimal closingAmount,
        String status,
        String remark,
        List<CustomerStatementItemResponse> items
) {
}
