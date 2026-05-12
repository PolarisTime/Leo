package com.leo.erp.contract.sales.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record SalesContractRequest(
        String contractNo,
        @jakarta.validation.constraints.NotBlank String customerName,
        @jakarta.validation.constraints.NotBlank String projectName,
        @NotNull LocalDate signDate,
        @NotNull LocalDate effectiveDate,
        @NotNull LocalDate expireDate,
        @jakarta.validation.constraints.NotBlank String salesName,
        String status,
        String remark,
        @Valid @NotEmpty List<SalesContractItemRequest> items
) {
}
