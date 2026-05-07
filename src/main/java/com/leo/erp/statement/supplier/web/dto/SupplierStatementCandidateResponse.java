package com.leo.erp.statement.supplier.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierStatementCandidateResponse(
        Long id,
        String inboundNo,
        String supplierName,
        String warehouseName,
        LocalDate inboundDate,
        String settlementMode,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status
) {
}
