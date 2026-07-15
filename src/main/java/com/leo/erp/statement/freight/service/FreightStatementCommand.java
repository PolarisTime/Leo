package com.leo.erp.statement.freight.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FreightStatementCommand(
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
        String attachment,
        String remark,
        List<FreightStatementItemCommand> items,
        Long carrierId
) {
}
