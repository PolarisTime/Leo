package com.leo.erp.report.inventory.web.dto;

import com.leo.erp.common.excel.annotation.ExportColumn;

import java.math.BigDecimal;

public record InventoryReportExportRow(
        @ExportColumn(header = "商品编码", order = 1)
        String materialCode,
        @ExportColumn(header = "品牌", order = 2)
        String brand,
        @ExportColumn(header = "材质", order = 3)
        String material,
        @ExportColumn(header = "类别", order = 4)
        String category,
        @ExportColumn(header = "规格", order = 5)
        String spec,
        @ExportColumn(header = "长度", order = 6)
        String length,
        @ExportColumn(header = "仓库", order = 7)
        String warehouseName,
        @ExportColumn(header = "批号", order = 8)
        String batchNo,
        @ExportColumn(header = "结存数量", order = 9)
        Integer quantity,
        @ExportColumn(header = "数量单位", order = 10)
        String quantityUnit,
        @ExportColumn(header = "结存重量(吨)", order = 11, format = "0.000")
        BigDecimal weightTon,
        @ExportColumn(header = "件重(吨)", order = 12, format = "0.000")
        BigDecimal pieceWeightTon,
        @ExportColumn(header = "单位", order = 13)
        String unit
) {
    public static InventoryReportExportRow from(InventoryReportResponse row) {
        return new InventoryReportExportRow(
                row.materialCode(),
                row.brand(),
                row.material(),
                row.category(),
                row.spec(),
                row.length(),
                row.warehouseName(),
                row.batchNo(),
                row.quantity(),
                row.quantityUnit(),
                row.weightTon(),
                row.pieceWeightTon(),
                row.unit()
        );
    }
}
