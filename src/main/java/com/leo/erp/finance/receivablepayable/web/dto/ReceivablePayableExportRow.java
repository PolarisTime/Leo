package com.leo.erp.finance.receivablepayable.web.dto;

import com.leo.erp.common.excel.annotation.ExportColumn;

import java.math.BigDecimal;

public record ReceivablePayableExportRow(
        @ExportColumn(header = "方向", order = 1)
        String direction,
        @ExportColumn(header = "往来单位类型", order = 2)
        String counterpartyType,
        @ExportColumn(header = "往来单位", order = 3)
        String counterpartyName,
        @ExportColumn(header = "期初余额", order = 4, format = "0.00")
        BigDecimal openingAmount,
        @ExportColumn(header = "发生金额", order = 5, format = "0.00")
        BigDecimal currentAmount,
        @ExportColumn(header = "已结算金额", order = 6, format = "0.00")
        BigDecimal settledAmount,
        @ExportColumn(header = "余额", order = 7, format = "0.00")
        BigDecimal balanceAmount,
        @ExportColumn(header = "来源单据数", order = 8)
        Long documentCount,
        @ExportColumn(header = "状态", order = 9)
        String status
) {
    public static ReceivablePayableExportRow from(ReceivablePayableResponse row) {
        return new ReceivablePayableExportRow(
                row.direction(),
                row.counterpartyType(),
                row.counterpartyName(),
                row.openingAmount(),
                row.currentAmount(),
                row.settledAmount(),
                row.balanceAmount(),
                row.documentCount(),
                row.status()
        );
    }
}
