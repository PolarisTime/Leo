package com.leo.erp.statement.freight.web.dto;

import com.leo.erp.logistics.bill.web.dto.FreightBillItemRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FreightStatementRequest(
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
        @Size(max = 500) String attachment,
        @Size(max = 255) String remark,
        @Valid @NotEmpty List<FreightBillItemRequest> items,
        Long carrierId
) {
}
