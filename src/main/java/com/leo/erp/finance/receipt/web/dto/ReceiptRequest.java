package com.leo.erp.finance.receipt.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReceiptRequest(
        String receiptNo,
        @jakarta.validation.constraints.NotBlank(message = "客户不能为空")
        String customerName,
        @jakarta.validation.constraints.NotBlank(message = "项目不能为空")
        String projectName,
        Long sourceStatementId,
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
}
