package com.leo.erp.contract.purchase.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record PurchaseContractRequest(
        String contractNo,
        Long supplierId,
        String supplierCode,
        @jakarta.validation.constraints.NotBlank String supplierName,
        @NotNull LocalDate signDate,
        @NotNull LocalDate effectiveDate,
        @NotNull LocalDate expireDate,
        @jakarta.validation.constraints.NotBlank String buyerName,
        String status,
        String remark,
        @Valid @NotEmpty List<PurchaseContractItemRequest> items
) {
    public PurchaseContractRequest(String contractNo,
                                   String supplierName,
                                   LocalDate signDate,
                                   LocalDate effectiveDate,
                                   LocalDate expireDate,
                                   String buyerName,
                                   String status,
                                   String remark,
                                   List<PurchaseContractItemRequest> items) {
        this(contractNo, null, null, supplierName, signDate, effectiveDate, expireDate, buyerName, status, remark,
                items);
    }
}
