package com.leo.erp.finance.payment.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PaymentRequest(
        @NotBlank(message = "付款单号不能为空")
        String paymentNo,
        @NotBlank(message = "业务类型不能为空")
        String businessType,
        @NotBlank(message = "往来单位不能为空")
        String counterpartyName,
        Long sourceStatementId,
        @NotNull(message = "付款日期不能为空")
        LocalDate paymentDate,
        @NotBlank(message = "付款方式不能为空")
        String payType,
        @NotNull(message = "金额不能为空")
        @DecimalMin(value = "0.00", message = "金额不能小于0")
        BigDecimal amount,
        @NotBlank(message = "状态不能为空")
        String status,
        @NotBlank(message = "经办人不能为空")
        String operatorName,
        String remark,
        @Valid
        List<PaymentAllocationRequest> items
) {
}
