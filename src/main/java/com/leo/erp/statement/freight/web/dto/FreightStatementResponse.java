package com.leo.erp.statement.freight.web.dto;

import com.leo.erp.attachment.web.dto.AttachmentResponse;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FreightStatementResponse(
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
        boolean deletedFlag,
        String signStatus,
        String attachment,
        List<AttachmentResponse> attachments,
        String remark,
        List<FreightBillItemResponse> items,
        Long carrierId
) {
    public FreightStatementResponse(Long id,
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
                                    boolean deletedFlag,
                                    String signStatus,
                                    String attachment,
                                    List<AttachmentResponse> attachments,
                                    String remark,
                                    List<FreightBillItemResponse> items) {
        this(id, statementNo, carrierCode, carrierName, settlementCompanyId, settlementCompanyName, startDate,
                endDate, totalWeight, totalFreight, paidAmount, unpaidAmount, status, deletedFlag, signStatus,
                attachment, attachments, remark, items, null);
    }

    public FreightStatementResponse(Long id,
                                    String statementNo,
                                    String carrierCode,
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
                                    List<FreightBillItemResponse> items) {
        this(id, statementNo, carrierCode, carrierName, null, null, startDate, endDate, totalWeight,
                totalFreight, paidAmount, unpaidAmount, status, false, signStatus, attachment, attachments, remark,
                items, null);
    }

    public FreightStatementResponse(Long id,
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
                                    List<AttachmentResponse> attachments,
                                    String remark,
                                    List<FreightBillItemResponse> items) {
        this(id, statementNo, null, carrierName, null, null, startDate, endDate, totalWeight, totalFreight,
                paidAmount, unpaidAmount, status, false, signStatus, attachment, attachments, remark, items, null);
    }
}
