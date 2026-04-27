package com.leo.erp.finance.receipt.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceiptRequest(
        @NotBlank(message = "收款单号不能为空")
        String receiptNo,
        @NotBlank(message = "客户不能为空")
        String customerName,
        @NotBlank(message = "项目不能为空")
        String projectName,
        Long sourceStatementId,
        @NotNull(message = "收款日期不能为空")
        LocalDate receiptDate,
        @NotBlank(message = "收款方式不能为空")
        String payType,
        @NotNull(message = "金额不能为空")
        @DecimalMin(value = "0.00", message = "金额不能小于0")
        BigDecimal amount,
        @NotBlank(message = "状态不能为空")
        String status,
        @NotBlank(message = "经办人不能为空")
        String operatorName,
        String remark
) {
}
