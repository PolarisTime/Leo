package com.leo.erp.statement.supplier.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SupplierStatementResponse(
        Long id,
        String statementNo,
        String sourceInboundNos,
        String supplierName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal purchaseAmount,
        BigDecimal paymentAmount,
        BigDecimal closingAmount,
        String status,
        String remark,
        List<SupplierStatementItemResponse> items
) {
}
