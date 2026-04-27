package com.leo.erp.finance.payment.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentResponse(
        Long id,
        String paymentNo,
        String businessType,
        String counterpartyName,
        Long sourceStatementId,
        LocalDate paymentDate,
        String payType,
        BigDecimal amount,
        String status,
        String operatorName,
        String remark
) {
}
