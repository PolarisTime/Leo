package com.leo.erp.finance.cashreversal.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashReversalResponse(
        Long id,
        String reversalNo,
        Long originalPaymentId,
        Long originalReceiptId,
        String counterpartyType,
        Long counterpartyId,
        String counterpartyCode,
        String counterpartyName,
        Long settlementCompanyId,
        String settlementCompanyName,
        LocalDate reversalDate,
        BigDecimal amount,
        String reason,
        String status,
        boolean deletedFlag,
        String operatorName,
        String remark
) {
}
