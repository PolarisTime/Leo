package com.leo.erp.finance.receivablepayable.web.dto;

import java.math.BigDecimal;

public record ReceivablePayableResponse(
        String id,
        String direction,
        String counterpartyType,
        String counterpartyName,
        BigDecimal recognizedAmount,
        BigDecimal settledAmount,
        BigDecimal balanceAmount,
        BigDecimal days0To30Amount,
        BigDecimal days31To60Amount,
        BigDecimal days61To90Amount,
        BigDecimal daysOver90Amount,
        Long entryCount,
        String status,
        String remark
) {
}
