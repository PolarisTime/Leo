package com.leo.erp.contract.purchase.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseContractResponse(
        Long id,
        String contractNo,
        String supplierName,
        LocalDate signDate,
        LocalDate effectiveDate,
        LocalDate expireDate,
        String buyerName,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        String remark,
        List<PurchaseContractItemResponse> items
) {
}
