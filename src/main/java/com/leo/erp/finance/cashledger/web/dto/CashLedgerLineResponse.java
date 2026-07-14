package com.leo.erp.finance.cashledger.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashLedgerLineResponse(
        LocalDate businessDate,
        String flowType,
        Long documentId,
        String documentNo,
        String counterpartyType,
        Long counterpartyId,
        String counterpartyName,
        String purpose,
        BigDecimal incomeAmount,
        BigDecimal expenseAmount,
        BigDecimal runningBalance,
        String operatorName,
        String remark
) {
}
