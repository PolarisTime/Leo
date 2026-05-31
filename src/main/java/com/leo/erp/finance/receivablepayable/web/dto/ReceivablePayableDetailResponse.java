package com.leo.erp.finance.receivablepayable.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReceivablePayableDetailResponse(
        String id,
        String direction,
        String counterpartyType,
        String counterpartyName,
        BigDecimal openingAmount,
        BigDecimal currentAmount,
        BigDecimal settledAmount,
        BigDecimal balanceAmount,
        Long documentCount,
        String status,
        String remark,
        List<ReceivablePayableDetailItemResponse> items
) {
}
