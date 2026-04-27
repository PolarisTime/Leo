package com.leo.erp.statement.freight.web.dto;

import com.leo.erp.attachment.web.dto.AttachmentResponse;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FreightStatementResponse(
        Long id,
        String statementNo,
        String sourceBillNos,
        String carrierName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalWeight,
        BigDecimal totalFreight,
        BigDecimal paidAmount,
        BigDecimal unpaidAmount,
        String status,
        String signStatus,
        String attachment,
        List<AttachmentResponse> attachments,
        String remark,
        List<FreightBillItemResponse> items
) {
}
