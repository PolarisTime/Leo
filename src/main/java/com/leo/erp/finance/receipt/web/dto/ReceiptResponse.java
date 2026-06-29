package com.leo.erp.finance.receipt.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReceiptResponse(
        Long id,
        String receiptNo,
        String customerCode,
        String customerName,
        Long projectId,
        String projectName,
        Long settlementCompanyId,
        String settlementCompanyName,
        Long sourceStatementId,
        LocalDate receiptDate,
        String payType,
        BigDecimal amount,
        String status,
        String operatorName,
        String remark,
        List<ReceiptAllocationResponse> items
) {
    public ReceiptResponse(Long id,
                           String receiptNo,
                           String customerName,
                           String projectName,
                           Long sourceStatementId,
                           LocalDate receiptDate,
                           String payType,
                           BigDecimal amount,
                           String status,
                           String operatorName,
                           String remark,
                           List<ReceiptAllocationResponse> items) {
        this(id, receiptNo, null, customerName, null, projectName, null, null, sourceStatementId, receiptDate, payType, amount, status, operatorName, remark, items);
    }

    public ReceiptResponse(Long id,
                           String receiptNo,
                           String customerCode,
                           String customerName,
                           Long projectId,
                           String projectName,
                           Long sourceStatementId,
                           LocalDate receiptDate,
                           String payType,
                           BigDecimal amount,
                           String status,
                           String operatorName,
                           String remark,
                           List<ReceiptAllocationResponse> items) {
        this(id, receiptNo, customerCode, customerName, projectId, projectName, null, null, sourceStatementId, receiptDate, payType, amount, status, operatorName, remark, items);
    }
}
