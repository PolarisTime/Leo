package com.leo.erp.contract.purchase.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record PurchaseContractRequest(
        String contractNo,
        @jakarta.validation.constraints.NotBlank String supplierName,
        String sourcePurchaseOrderNos,
        @NotNull LocalDate signDate,
        @NotNull LocalDate effectiveDate,
        @NotNull LocalDate expireDate,
        @jakarta.validation.constraints.NotBlank String buyerName,
        String status,
        String remark,
        @Valid @NotEmpty List<PurchaseContractItemRequest> items
) {
}
