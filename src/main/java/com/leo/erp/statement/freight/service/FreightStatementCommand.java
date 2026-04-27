package com.leo.erp.statement.freight.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FreightStatementCommand(
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
        List<Long> attachmentIds,
        String remark,
        List<FreightStatementItemCommand> items
) {
}
