package com.leo.erp.statement.freight.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FreightStatementCommand(
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
        String remark,
        List<FreightStatementItemCommand> items
) {
    public FreightStatementCommand(String statementNo,
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
                                   List<FreightStatementItemCommand> items) {
        this(statementNo, null, carrierName, startDate, endDate, totalWeight, totalFreight, paidAmount, unpaidAmount, status, signStatus, attachment, remark, items);
    }
}
