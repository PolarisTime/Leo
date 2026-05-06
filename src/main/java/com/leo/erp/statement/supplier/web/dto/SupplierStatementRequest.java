package com.leo.erp.statement.supplier.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SupplierStatementRequest(
        @NotBlank String statementNo,
        String sourceInboundNos,
        @NotBlank String supplierName,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @DecimalMin("0.00") BigDecimal purchaseAmount,
        @DecimalMin("0.00") BigDecimal paymentAmount,
        @DecimalMin("0.00") BigDecimal closingAmount,
        String status,
        String remark,
        @Valid @NotEmpty List<SupplierStatementItemRequest> items
) {
}
