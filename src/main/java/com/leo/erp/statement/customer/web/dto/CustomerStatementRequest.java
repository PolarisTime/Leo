package com.leo.erp.statement.customer.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CustomerStatementRequest(
        String statementNo,
        String customerCode,
        @jakarta.validation.constraints.NotBlank String customerName,
        Long projectId,
        @jakarta.validation.constraints.NotBlank String projectName,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @DecimalMin("0.00") BigDecimal salesAmount,
        @DecimalMin("0.00") BigDecimal receiptAmount,
        @DecimalMin("0.00") BigDecimal closingAmount,
        String status,
        String remark,
        @Valid @NotEmpty List<CustomerStatementItemRequest> items
) {
    public CustomerStatementRequest(String statementNo,
                                    String customerName,
                                    String projectName,
                                    LocalDate startDate,
                                    LocalDate endDate,
                                    BigDecimal salesAmount,
                                    BigDecimal receiptAmount,
                                    BigDecimal closingAmount,
                                    String status,
                                    String remark,
                                    List<CustomerStatementItemRequest> items) {
        this(statementNo, null, customerName, null, projectName, startDate, endDate, salesAmount, receiptAmount, closingAmount, status, remark, items);
    }
}
