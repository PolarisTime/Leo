package com.leo.erp.finance.receipt.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReceiptRequest(
        String receiptNo,
        Long customerId,
        String customerCode,
        @jakarta.validation.constraints.NotBlank(message = "客户不能为空")
        String customerName,
        Long projectId,
        @jakarta.validation.constraints.NotBlank(message = "项目不能为空")
        String projectName,
        Long settlementCompanyId,
        String settlementCompanyName,
        Long sourceCustomerStatementId,
        @NotNull(message = "收款日期不能为空")
        LocalDate receiptDate,
        @jakarta.validation.constraints.NotBlank(message = "收款方式不能为空")
        String payType,
        @NotNull(message = "金额不能为空")
        @DecimalMin(value = "0.00", message = "金额不能小于0")
        BigDecimal amount,
        @jakarta.validation.constraints.NotBlank(message = "状态不能为空")
        String status,
        @jakarta.validation.constraints.NotBlank(message = "经办人不能为空")
        String operatorName,
        String remark,
        @Valid
        List<ReceiptAllocationRequest> items
) {
    public ReceiptRequest(String receiptNo,
                          String customerCode,
                          String customerName,
                          Long projectId,
                          String projectName,
                          Long settlementCompanyId,
                          String settlementCompanyName,
                          Long sourceCustomerStatementId,
                          LocalDate receiptDate,
                          String payType,
                          BigDecimal amount,
                          String status,
                          String operatorName,
                          String remark,
                          List<ReceiptAllocationRequest> items) {
        this(receiptNo, null, customerCode, customerName, projectId, projectName, settlementCompanyId,
                settlementCompanyName, sourceCustomerStatementId, receiptDate, payType, amount, status,
                operatorName, remark, items);
    }

    public ReceiptRequest(String receiptNo,
                          String customerName,
                          String projectName,
                          Long sourceStatementId,
                          LocalDate receiptDate,
                          String payType,
                          BigDecimal amount,
                          String status,
                          String operatorName,
                          String remark,
                          List<ReceiptAllocationRequest> items) {
        this(receiptNo, null, null, customerName, null, projectName, null, null, sourceStatementId,
                receiptDate, payType, amount, status, operatorName, remark, items);
    }

    public ReceiptRequest(String receiptNo,
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
                          List<ReceiptAllocationRequest> items) {
        this(receiptNo, null, customerCode, customerName, projectId, projectName, null, null,
                sourceStatementId, receiptDate, payType, amount, status, operatorName, remark, items);
    }

    @Deprecated(forRemoval = false)
    public Long sourceStatementId() {
        return sourceCustomerStatementId;
    }
}
