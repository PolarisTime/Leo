package com.leo.erp.purchase.refund.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PurchaseRefundSourceCandidateResponse(
        Long id,
        String orderNo,
        String supplierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        LocalDateTime orderDate,
        String status,
        Integer refundableQuantity,
        BigDecimal refundableWeight,
        BigDecimal refundableAmount
) {
}
