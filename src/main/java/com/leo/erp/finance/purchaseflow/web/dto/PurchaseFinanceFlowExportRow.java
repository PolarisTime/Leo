package com.leo.erp.finance.purchaseflow.web.dto;

import com.leo.erp.common.excel.annotation.ExportColumn;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseFinanceFlowExportRow(
        @ExportColumn(header = "流转序号", order = 1)
        long flowSequence,
        @ExportColumn(header = "业务日期", order = 2)
        LocalDate businessDate,
        @ExportColumn(header = "单据类型", order = 3)
        String documentType,
        @ExportColumn(header = "单号", order = 4)
        String documentNo,
        @ExportColumn(header = "行号", order = 5)
        Integer lineNo,
        @ExportColumn(header = "来源单号/行号", order = 6)
        String sourceReference,
        @ExportColumn(header = "物料编码", order = 7)
        String materialCode,
        @ExportColumn(header = "物料", order = 8)
        String materialName,
        @ExportColumn(header = "数量", order = 9)
        Integer quantity,
        @ExportColumn(header = "数量单位", order = 10)
        String quantityUnit,
        @ExportColumn(header = "实际重量(吨)", order = 11, format = "0.00000000")
        BigDecimal actualWeightTon,
        @ExportColumn(header = "单价", order = 12, format = "0.00")
        BigDecimal unitPrice,
        @ExportColumn(header = "行金额", order = 13, format = "0.00")
        BigDecimal lineAmount,
        @ExportColumn(header = "支出", order = 14, format = "0.00")
        BigDecimal expenseAmount,
        @ExportColumn(header = "收入", order = 15, format = "0.00")
        BigDecimal incomeAmount,
        @ExportColumn(header = "历史调整方向", order = 16)
        String adjustmentDirection,
        @ExportColumn(header = "历史余额影响", order = 17)
        String adjustmentEffect,
        @ExportColumn(header = "状态", order = 18)
        String status,
        @ExportColumn(header = "是否生效", order = 19)
        boolean effective,
        @ExportColumn(header = "备注", order = 20)
        String remark
) {
    public static PurchaseFinanceFlowExportRow from(PurchaseFinanceFlowLineResponse row) {
        return new PurchaseFinanceFlowExportRow(
                row.flowSequence(),
                row.businessDate(),
                row.documentType(),
                row.documentNo(),
                row.lineNo(),
                sourceReference(row),
                row.materialCode(),
                row.materialName(),
                row.quantity(),
                row.quantityUnit(),
                row.actualWeightTon(),
                row.unitPrice(),
                row.lineAmount(),
                row.expenseAmount(),
                row.incomeAmount(),
                row.adjustmentDirection(),
                row.adjustmentEffect(),
                row.status(),
                row.effective(),
                row.remark()
        );
    }

    private static String sourceReference(PurchaseFinanceFlowLineResponse row) {
        if (row.sourceDocumentNo() == null || row.sourceDocumentNo().isBlank()) {
            return "-";
        }
        return row.sourceLineNo() == null
                ? row.sourceDocumentNo()
                : row.sourceDocumentNo() + "/" + row.sourceLineNo();
    }
}
