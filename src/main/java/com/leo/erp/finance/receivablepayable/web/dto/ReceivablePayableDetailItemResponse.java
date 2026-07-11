package com.leo.erp.finance.receivablepayable.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceivablePayableDetailItemResponse(
        String id,
        String entryRole,
        String sourceType,
        Long sourceDocumentId,
        String documentNo,
        String sourceNo,
        String projectName,
        Long settlementCompanyId,
        String settlementCompanyName,
        String reconciliationStatus,
        LocalDate accountingDate,
        LocalDate dueDate,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        BigDecimal balanceAmount,
        Integer ageDays,
        String status,
        String remark
) {
    public ReceivablePayableDetailItemResponse(String id,
                                               String entryRole,
                                               String sourceType,
                                               Long sourceDocumentId,
                                               String documentNo,
                                               String sourceNo,
                                               String projectName,
                                               String reconciliationStatus,
                                               LocalDate accountingDate,
                                               LocalDate dueDate,
                                               BigDecimal debitAmount,
                                               BigDecimal creditAmount,
                                               BigDecimal balanceAmount,
                                               Integer ageDays,
                                               String status,
                                               String remark) {
        this(id, entryRole, sourceType, sourceDocumentId, documentNo, sourceNo, projectName,
                null, null, reconciliationStatus, accountingDate, dueDate, debitAmount,
                creditAmount, balanceAmount, ageDays, status, remark);
    }
}
