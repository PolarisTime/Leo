package com.leo.erp.statement.freight.service;

import com.leo.erp.attachment.service.AttachmentView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FreightStatementView(
        Long id,
        String statementNo,
        String carrierCode,
        String carrierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalWeight,
        BigDecimal totalFreight,
        BigDecimal paidAmount,
        BigDecimal unpaidAmount,
        String status,
        String signStatus,
        String attachment,
        List<AttachmentView> attachments,
        String remark,
        List<FreightStatementItemView> items
) {
    public FreightStatementView(Long id,
                                String statementNo,
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
                                List<AttachmentView> attachments,
                                String remark,
                                List<FreightStatementItemView> items) {
        this(id, statementNo, null, carrierName, null, null, startDate, endDate, totalWeight, totalFreight,
                paidAmount, unpaidAmount, status, signStatus, attachment, attachments, remark, items);
    }
}
