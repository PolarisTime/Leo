package com.leo.erp.finance.payment.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentAllocationRequest(
        Long id,
        Long sourceStatementId,
        Long sourceSupplierStatementId,
        Long sourceFreightStatementId,
        @NotNull(message = "核销金额不能为空")
        @DecimalMin(value = "0.00", message = "核销金额不能小于0")
        BigDecimal allocatedAmount
) {
    public PaymentAllocationRequest(Long id, Long sourceStatementId, BigDecimal allocatedAmount) {
        this(id, sourceStatementId, null, null, allocatedAmount);
    }

    @AssertTrue(message = "核销对账单来源必须且只能填写一种")
    public boolean isStatementSourceValid() {
        int typedSourceCount = (sourceSupplierStatementId == null ? 0 : 1)
                + (sourceFreightStatementId == null ? 0 : 1);
        if (typedSourceCount > 1) {
            return false;
        }
        Long typedSourceId = sourceSupplierStatementId == null
                ? sourceFreightStatementId
                : sourceSupplierStatementId;
        if (sourceStatementId == null && typedSourceId == null) {
            return false;
        }
        return sourceStatementId == null
                || typedSourceId == null
                || sourceStatementId.equals(typedSourceId);
    }
}
