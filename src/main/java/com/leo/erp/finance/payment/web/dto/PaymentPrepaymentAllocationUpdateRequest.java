package com.leo.erp.finance.payment.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PaymentPrepaymentAllocationUpdateRequest(
        @NotNull(message = "采购预付款核销明细不能为空")
        @Valid
        List<PaymentAllocationRequest> items
) {
}
