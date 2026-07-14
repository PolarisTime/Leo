package com.leo.erp.finance.cashreversal.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashReversalRequest(
        String reversalNo,
        Long originalPaymentId,
        Long originalReceiptId,
        @NotNull(message = "冲销日期不能为空")
        LocalDate reversalDate,
        @NotNull(message = "冲销金额不能为空")
        @DecimalMin(value = "0.01", message = "冲销金额必须大于0")
        BigDecimal amount,
        @NotBlank(message = "冲销原因不能为空")
        @Size(max = 255, message = "冲销原因长度不能超过255个字符")
        String reason,
        @NotBlank(message = "状态不能为空")
        String status,
        @NotBlank(message = "经办人不能为空")
        @Size(max = 32, message = "经办人长度不能超过32个字符")
        String operatorName,
        @Size(max = 255, message = "备注长度不能超过255个字符")
        String remark
) {
}
