package com.leo.erp.finance.purchaseflow.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record PurchaseFinanceFlowFilter(
        @NotNull @Positive Long settlementCompanyId,
        @NotNull @Positive Long supplierId,
        String documentType,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        String materialKeyword,
        @Positive Long purchaseOrderId
) {
}
