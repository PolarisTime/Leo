package com.leo.erp.finance.receivablepayable.web.dto;

import java.math.BigDecimal;

public record ReceivablePayableResponse(
        Long id,
        String direction,
        String counterpartyType,
        String counterpartyName,
        BigDecimal openingAmount,
        BigDecimal currentAmount,
        BigDecimal settledAmount,
        BigDecimal balanceAmount,
        String status,
        String remark
) {
}
