package com.leo.erp.master.material.web.dto;

import com.leo.erp.common.excel.annotation.ExportColumn;
import com.leo.erp.common.excel.annotation.ImportColumn;

public record MaterialImportDTO(
        @ImportColumn(header = "商品编码", required = false, order = 1, example = "RB400-18-12")
        @ExportColumn(header = "商品编码", order = 1)
        String materialCode,

        @ImportColumn(header = "品牌", required = true, order = 2, example = "敬业")
        @ExportColumn(header = "品牌", order = 2)
        String brand,

        @ImportColumn(header = "材质", required = true, order = 3, example = "HRB400")
        @ExportColumn(header = "材质", order = 3)
        String material,

        @ImportColumn(header = "类别", required = true, order = 4, example = "螺纹钢")
        @ExportColumn(header = "类别", order = 4)
        String category,

        @ImportColumn(header = "规格", required = true, order = 5, example = "18")
        @ExportColumn(header = "规格", order = 5)
        String spec,

        @ImportColumn(header = "长度", required = false, order = 6, example = "12米")
        @ExportColumn(header = "长度", order = 6)
        String length,

        @ImportColumn(header = "单位", required = true, order = 7, example = "吨")
        @ExportColumn(header = "单位", order = 7)
        String unit,

        @ImportColumn(header = "数量单位", required = false, order = 8, example = "件")
        @ExportColumn(header = "数量单位", order = 8)
        String quantityUnit,

        @ImportColumn(header = "件重(吨)", required = true, order = 9, example = "0.002")
        @ExportColumn(header = "件重(吨)", order = 9, format = "0.000")
        String pieceWeightTon,

        @ImportColumn(header = "每件支数", required = false, order = 10, example = "1")
        @ExportColumn(header = "每件支数", order = 10)
        String piecesPerBundle,

        @ImportColumn(header = "单价", required = false, order = 11, example = "3500.00")
        @ExportColumn(header = "单价", order = 11, format = "0.00")
        String unitPrice,

        @ImportColumn(header = "备注", required = false, order = 12, example = "示例数据，可删除")
        @ExportColumn(header = "备注", order = 12)
        String remark
) {
}
