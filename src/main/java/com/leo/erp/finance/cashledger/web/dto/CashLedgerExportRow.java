package com.leo.erp.finance.cashledger.web.dto;

import com.leo.erp.common.excel.annotation.ExportColumn;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashLedgerExportRow(
        @ExportColumn(header = "业务日期", order = 1)
        LocalDate businessDate,
        @ExportColumn(header = "流水类型", order = 2)
        String flowType,
        @ExportColumn(header = "单据ID", order = 3)
        Long documentId,
        @ExportColumn(header = "单据编号", order = 4)
        String documentNo,
        @ExportColumn(header = "往来类型", order = 5)
        String counterpartyType,
        @ExportColumn(header = "往来单位ID", order = 6)
        Long counterpartyId,
        @ExportColumn(header = "往来单位", order = 7)
        String counterpartyName,
        @ExportColumn(header = "业务用途", order = 8)
        String purpose,
        @ExportColumn(header = "收入金额", order = 9, format = "0.00")
        BigDecimal incomeAmount,
        @ExportColumn(header = "支出金额", order = 10, format = "0.00")
        BigDecimal expenseAmount,
        @ExportColumn(header = "流水余额", order = 11, format = "0.00")
        BigDecimal runningBalance,
        @ExportColumn(header = "操作人", order = 12)
        String operatorName,
        @ExportColumn(header = "备注", order = 13)
        String remark
) {

    public static CashLedgerExportRow from(CashLedgerLineResponse line) {
        return new CashLedgerExportRow(
                line.businessDate(),
                flowTypeName(line.flowType()),
                line.documentId(),
                line.documentNo(),
                line.counterpartyType(),
                line.counterpartyId(),
                line.counterpartyName(),
                line.purpose(),
                line.incomeAmount(),
                line.expenseAmount(),
                line.runningBalance(),
                line.operatorName(),
                line.remark()
        );
    }

    private static String flowTypeName(String flowType) {
        return switch (flowType) {
            case "RECEIPT" -> "收款";
            case "PAYMENT" -> "付款";
            case "PAYMENT_REVERSAL" -> "付款冲销";
            case "RECEIPT_REVERSAL" -> "收款冲销";
            default -> flowType;
        };
    }
}
