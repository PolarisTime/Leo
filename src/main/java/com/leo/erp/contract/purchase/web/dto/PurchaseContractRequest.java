package com.leo.erp.contract.purchase.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record PurchaseContractRequest(
        @NotBlank String contractNo,
        @NotBlank String supplierName,
        @NotNull LocalDate signDate,
        @NotNull LocalDate effectiveDate,
        @NotNull LocalDate expireDate,
        @NotBlank String buyerName,
        String status,
        String remark,
        @Valid @NotEmpty List<PurchaseContractItemRequest> items
) {
}
