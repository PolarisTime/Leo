package com.leo.erp.finance.payment.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentAllocationRequest(
        Long id,
        @NotNull(message = "核销对账单不能为空")
        Long sourceStatementId,
        @NotNull(message = "核销金额不能为空")
        @DecimalMin(value = "0.00", message = "核销金额不能小于0")
        BigDecimal allocatedAmount
) {
}
