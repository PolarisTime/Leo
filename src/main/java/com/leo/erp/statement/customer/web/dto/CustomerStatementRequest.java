package com.leo.erp.statement.customer.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CustomerStatementRequest(
        @NotBlank String statementNo,
        String sourceOrderNos,
        @NotBlank String customerName,
        @NotBlank String projectName,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @DecimalMin("0.00") BigDecimal salesAmount,
        BigDecimal receiptAmount,
        BigDecimal closingAmount,
        String status,
        String remark,
        @Valid @NotEmpty List<CustomerStatementItemRequest> items
) {
}
