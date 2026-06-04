package com.leo.erp.finance.receivablepayable.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReceivablePayableDetailResponse(
        String id,
        String direction,
        String counterpartyType,
        String counterpartyCode,
        String counterpartyName,
        String reconciliationStatus,
        BigDecimal recognizedAmount,
        BigDecimal settledAmount,
        BigDecimal balanceAmount,
        BigDecimal days0To30Amount,
        BigDecimal days31To60Amount,
        BigDecimal days61To90Amount,
        BigDecimal daysOver90Amount,
        Long entryCount,
        String status,
        String remark,
        List<ReceivablePayableDetailItemResponse> items
) {
    public ReceivablePayableDetailResponse(String id,
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
                                           String remark,
                                           List<ReceivablePayableDetailItemResponse> items) {
        this(id, direction, counterpartyType, null, counterpartyName, "未对账", recognizedAmount, settledAmount, balanceAmount,
                days0To30Amount, days31To60Amount, days61To90Amount, daysOver90Amount, entryCount, status, remark, items);
    }
}
