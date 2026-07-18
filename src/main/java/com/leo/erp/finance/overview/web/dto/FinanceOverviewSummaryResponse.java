package com.leo.erp.finance.overview.web.dto;

import java.math.BigDecimal;

public record FinanceOverviewSummaryResponse(
        BigDecimal receivableAmount,
        BigDecimal receivedAmount,
        BigDecimal unreceivedAmount,
        BigDecimal advanceReceiptAmount,
        BigDecimal payableAmount,
        BigDecimal paidAmount,
        BigDecimal unpaidAmount,
        BigDecimal advancePaymentAmount
) {
}
