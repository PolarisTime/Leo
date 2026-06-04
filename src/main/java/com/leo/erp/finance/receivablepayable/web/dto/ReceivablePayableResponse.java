package com.leo.erp.finance.receivablepayable.web.dto;

import java.math.BigDecimal;

public record ReceivablePayableResponse(
        String id,
        String direction,
        String counterpartyType,
        String counterpartyCode,
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
    public ReceivablePayableResponse(String id,
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
                                     String remark) {
        this(id, direction, counterpartyType, null, counterpartyName, recognizedAmount, settledAmount, balanceAmount,
                days0To30Amount, days31To60Amount, days61To90Amount, daysOver90Amount, entryCount, status, remark);
    }
}
