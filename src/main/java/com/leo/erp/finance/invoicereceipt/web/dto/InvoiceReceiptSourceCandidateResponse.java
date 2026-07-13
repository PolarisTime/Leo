package com.leo.erp.finance.invoicereceipt.web.dto;

import com.leo.erp.finance.common.web.dto.InvoiceSourceCandidateItemResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InvoiceReceiptSourceCandidateResponse(
        Long id,
        String orderNo,
        Long supplierId,
        String supplierCode,
        String supplierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        LocalDateTime orderDate,
        String buyerName,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        List<InvoiceSourceCandidateItemResponse> items
) {
}
