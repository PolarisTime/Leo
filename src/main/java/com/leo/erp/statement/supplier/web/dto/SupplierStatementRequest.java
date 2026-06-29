package com.leo.erp.statement.supplier.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SupplierStatementRequest(
        String statementNo,
        String supplierCode,
        @jakarta.validation.constraints.NotBlank String supplierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @DecimalMin("0.00") BigDecimal purchaseAmount,
        @DecimalMin("0.00") BigDecimal paymentAmount,
        @DecimalMin("0.00") BigDecimal closingAmount,
        String status,
        String remark,
        @Valid @NotEmpty List<SupplierStatementItemRequest> items
) {
    public SupplierStatementRequest(String statementNo,
                                    String supplierName,
                                    LocalDate startDate,
                                    LocalDate endDate,
                                    BigDecimal purchaseAmount,
                                    BigDecimal paymentAmount,
                                    BigDecimal closingAmount,
                                    String status,
                                    String remark,
                                    List<SupplierStatementItemRequest> items) {
        this(statementNo, null, supplierName, null, null, startDate, endDate, purchaseAmount,
                paymentAmount, closingAmount, status, remark, items);
    }

    public SupplierStatementRequest(String statementNo,
                                    String supplierCode,
                                    String supplierName,
                                    LocalDate startDate,
                                    LocalDate endDate,
                                    BigDecimal purchaseAmount,
                                    BigDecimal paymentAmount,
                                    BigDecimal closingAmount,
                                    String status,
                                    String remark,
                                    List<SupplierStatementItemRequest> items) {
        this(statementNo, supplierCode, supplierName, null, null, startDate, endDate, purchaseAmount,
                paymentAmount, closingAmount, status, remark, items);
    }
}
