package com.leo.erp.finance.payment.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentAllocationRequest(
        Long id,
        Long sourceStatementId,
        Long sourceFreightStatementId,
        @NotNull(message = "核销金额不能为空")
        @DecimalMin(value = "0.00", message = "核销金额不能小于0")
        BigDecimal allocatedAmount
) {
    public PaymentAllocationRequest(Long id, Long sourceStatementId, BigDecimal allocatedAmount) {
        this(id, sourceStatementId, null, allocatedAmount);
    }

    @AssertTrue(message = "物流对账单核销来源不能为空且必须保持一致")
    public boolean isStatementSourceValid() {
        if (sourceStatementId == null && sourceFreightStatementId == null) {
            return false;
        }
        return sourceStatementId == null
                || sourceFreightStatementId == null
                || sourceStatementId.equals(sourceFreightStatementId);
    }
}
