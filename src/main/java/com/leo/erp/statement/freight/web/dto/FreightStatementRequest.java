package com.leo.erp.statement.freight.web.dto;

import com.leo.erp.logistics.bill.web.dto.FreightBillItemRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FreightStatementRequest(
        String statementNo,
        String carrierCode,
        @jakarta.validation.constraints.NotBlank String carrierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @DecimalMin("0.000") BigDecimal totalWeight,
        @NotNull @DecimalMin("0.00") BigDecimal totalFreight,
        BigDecimal paidAmount,
        BigDecimal unpaidAmount,
        String status,
        String signStatus,
        @Size(max = 500) String attachment,
        @Size(max = 255) String remark,
        @Valid @NotEmpty List<FreightBillItemRequest> items,
        Long carrierId
) {
    public FreightStatementRequest(String statementNo,
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
                                   String remark,
                                   List<FreightBillItemRequest> items) {
        this(statementNo, carrierCode, carrierName, settlementCompanyId, settlementCompanyName, startDate, endDate,
                totalWeight, totalFreight, paidAmount, unpaidAmount, status, signStatus, attachment, remark, items,
                null);
    }

    public FreightStatementRequest(String statementNo,
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
                                   String remark,
                                   List<FreightBillItemRequest> items) {
        this(statementNo, null, carrierName, null, null, startDate, endDate, totalWeight, totalFreight,
                paidAmount, unpaidAmount, status, signStatus, attachment, remark, items, null);
    }

    public FreightStatementRequest(String statementNo,
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
                                   String remark,
                                   List<FreightBillItemRequest> items) {
        this(statementNo, carrierCode, carrierName, null, null, startDate, endDate, totalWeight,
                totalFreight, paidAmount, unpaidAmount, status, signStatus, attachment, remark, items, null);
    }
}
