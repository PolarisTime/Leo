package com.leo.erp.finance.invoiceissue.web.dto;

import com.leo.erp.finance.common.web.dto.InvoiceSourceCandidateItemResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceIssueSourceCandidateResponse(
        Long id,
        String orderNo,
        String customerCode,
        Long customerId,
        String customerName,
        Long projectId,
        String projectName,
        Long settlementCompanyId,
        String settlementCompanyName,
        LocalDate deliveryDate,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        List<InvoiceSourceCandidateItemResponse> items
) {
}
