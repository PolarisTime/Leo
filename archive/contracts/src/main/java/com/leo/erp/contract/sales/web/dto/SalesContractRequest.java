package com.leo.erp.contract.sales.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record SalesContractRequest(
        String contractNo,
        Long customerId,
        String customerCode,
        @jakarta.validation.constraints.NotBlank String customerName,
        Long projectId,
        @jakarta.validation.constraints.NotBlank String projectName,
        @NotNull LocalDate signDate,
        @NotNull LocalDate effectiveDate,
        @NotNull LocalDate expireDate,
        @jakarta.validation.constraints.NotBlank String salesName,
        String status,
        String remark,
        @Valid @NotEmpty List<SalesContractItemRequest> items
) {
    public SalesContractRequest(String contractNo,
                                String customerName,
                                String projectName,
                                LocalDate signDate,
                                LocalDate effectiveDate,
                                LocalDate expireDate,
                                String salesName,
                                String status,
                                String remark,
                                List<SalesContractItemRequest> items) {
        this(contractNo, null, null, customerName, null, projectName, signDate, effectiveDate, expireDate, salesName,
                status, remark, items);
    }
}
