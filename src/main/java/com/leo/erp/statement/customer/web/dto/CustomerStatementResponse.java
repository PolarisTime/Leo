package com.leo.erp.statement.customer.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CustomerStatementResponse(
        Long id,
        String statementNo,
        String customerCode,
        String customerName,
        Long projectId,
        String projectName,
        Long settlementCompanyId,
        String settlementCompanyName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal salesAmount,
        BigDecimal receiptAmount,
        BigDecimal closingAmount,
        String status,
        String remark,
        List<CustomerStatementItemResponse> items
) {
    public CustomerStatementResponse(Long id,
                                     String statementNo,
                                     String customerCode,
                                     String customerName,
                                     Long projectId,
                                     String projectName,
                                     LocalDate startDate,
                                     LocalDate endDate,
                                     BigDecimal salesAmount,
                                     BigDecimal receiptAmount,
                                     BigDecimal closingAmount,
                                     String status,
                                     String remark,
                                     List<CustomerStatementItemResponse> items) {
        this(id, statementNo, customerCode, customerName, projectId, projectName, null, null,
                startDate, endDate, salesAmount, receiptAmount, closingAmount, status, remark, items);
    }

    public CustomerStatementResponse(Long id,
                                     String statementNo,
                                     String customerName,
                                     String projectName,
                                     LocalDate startDate,
                                     LocalDate endDate,
                                     BigDecimal salesAmount,
                                     BigDecimal receiptAmount,
                                     BigDecimal closingAmount,
                                     String status,
                                     String remark,
                                     List<CustomerStatementItemResponse> items) {
        this(id, statementNo, null, customerName, null, projectName, null, null, startDate, endDate,
                salesAmount, receiptAmount, closingAmount, status, remark, items);
    }
}
