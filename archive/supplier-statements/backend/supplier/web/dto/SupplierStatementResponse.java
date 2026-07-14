package com.leo.erp.statement.supplier.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SupplierStatementResponse(
        Long id,
        String statementNo,
        String supplierCode,
        String supplierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal purchaseAmount,
        BigDecimal paymentAmount,
        BigDecimal closingAmount,
        String status,
        boolean deletedFlag,
        String remark,
        List<SupplierStatementItemResponse> items,
        Long supplierId
) {
    public SupplierStatementResponse(Long id,
                                     String statementNo,
                                     String supplierCode,
                                     String supplierName,
                                     Long settlementCompanyId,
                                     String settlementCompanyName,
                                     LocalDate startDate,
                                     LocalDate endDate,
                                     BigDecimal purchaseAmount,
                                     BigDecimal paymentAmount,
                                     BigDecimal closingAmount,
                                     String status,
                                     boolean deletedFlag,
                                     String remark,
                                     List<SupplierStatementItemResponse> items) {
        this(id, statementNo, supplierCode, supplierName, settlementCompanyId, settlementCompanyName, startDate,
                endDate, purchaseAmount, paymentAmount, closingAmount, status, deletedFlag, remark, items, null);
    }

    public SupplierStatementResponse(Long id,
                                     String statementNo,
                                     String supplierCode,
                                     String supplierName,
                                     Long settlementCompanyId,
                                     String settlementCompanyName,
                                     LocalDate startDate,
                                     LocalDate endDate,
                                     BigDecimal purchaseAmount,
                                     BigDecimal paymentAmount,
                                     BigDecimal closingAmount,
                                     String status,
                                     String remark,
                                     List<SupplierStatementItemResponse> items) {
        this(id, statementNo, supplierCode, supplierName, settlementCompanyId, settlementCompanyName,
                startDate, endDate, purchaseAmount, paymentAmount, closingAmount, status, false, remark, items, null);
    }

    public SupplierStatementResponse(Long id,
                                     String statementNo,
                                     String supplierCode,
                                     String supplierName,
                                     LocalDate startDate,
                                     LocalDate endDate,
                                     BigDecimal purchaseAmount,
                                     BigDecimal paymentAmount,
                                     BigDecimal closingAmount,
                                     String status,
                                     String remark,
                                     List<SupplierStatementItemResponse> items) {
        this(id, statementNo, supplierCode, supplierName, null, null, startDate, endDate,
                purchaseAmount, paymentAmount, closingAmount, status, false, remark, items, null);
    }

    public SupplierStatementResponse(Long id,
                                     String statementNo,
                                     String supplierName,
                                     LocalDate startDate,
                                     LocalDate endDate,
                                     BigDecimal purchaseAmount,
                                     BigDecimal paymentAmount,
                                     BigDecimal closingAmount,
                                     String status,
                                     String remark,
                                     List<SupplierStatementItemResponse> items) {
        this(id, statementNo, null, supplierName, null, null, startDate, endDate,
                purchaseAmount, paymentAmount, closingAmount, status, false, remark, items, null);
    }
}
