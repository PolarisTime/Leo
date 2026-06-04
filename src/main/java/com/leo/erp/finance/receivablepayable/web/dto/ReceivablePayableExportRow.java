package com.leo.erp.finance.receivablepayable.web.dto;

import com.leo.erp.common.excel.annotation.ExportColumn;

import java.math.BigDecimal;

public record ReceivablePayableExportRow(
        @ExportColumn(header = "方向", order = 1)
        String direction,
        @ExportColumn(header = "往来单位类型", order = 2)
        String counterpartyType,
        @ExportColumn(header = "往来单位编码", order = 3)
        String counterpartyCode,
        @ExportColumn(header = "往来单位", order = 4)
        String counterpartyName,
        @ExportColumn(header = "确认金额", order = 5, format = "0.00")
        BigDecimal recognizedAmount,
        @ExportColumn(header = "已结算金额", order = 6, format = "0.00")
        BigDecimal settledAmount,
        @ExportColumn(header = "余额", order = 7, format = "0.00")
        BigDecimal balanceAmount,
        @ExportColumn(header = "0-30天", order = 8, format = "0.00")
        BigDecimal days0To30Amount,
        @ExportColumn(header = "31-60天", order = 9, format = "0.00")
        BigDecimal days31To60Amount,
        @ExportColumn(header = "61-90天", order = 10, format = "0.00")
        BigDecimal days61To90Amount,
        @ExportColumn(header = "90天以上", order = 11, format = "0.00")
        BigDecimal daysOver90Amount,
        @ExportColumn(header = "账簿分录数", order = 12)
        Long entryCount,
        @ExportColumn(header = "状态", order = 13)
        String status
) {
    public static ReceivablePayableExportRow from(ReceivablePayableResponse row) {
        return new ReceivablePayableExportRow(
                row.direction(),
                row.counterpartyType(),
                row.counterpartyCode(),
                row.counterpartyName(),
                row.recognizedAmount(),
                row.settledAmount(),
                row.balanceAmount(),
                row.days0To30Amount(),
                row.days31To60Amount(),
                row.days61To90Amount(),
                row.daysOver90Amount(),
                row.entryCount(),
                row.status()
        );
    }
}
