package com.leo.erp.market.pricelist.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对照矩阵(只读投影)。
 * <p>{@code asOf} 缺省 = 当前时刻; 每个 (供应商, 品牌) 取该时刻之前 {@code released_at} 最大的未删除版本。</p>
 */
public record SupplierPriceMatrixResponse(
        LocalDateTime asOf,
        List<MatrixColumn> columns,
        List<MatrixRow> rows
) {

    /** 矩阵列 = 一个 (供应商, 品牌) 的生效版本。 */
    public record MatrixColumn(
            Long supplierId,
            String supplierName,
            String brandName,
            Long listId,
            LocalDateTime releasedAt,
            String warehouse
    ) {
    }

    /** 矩阵行 = 一个条目键。 */
    public record MatrixRow(
            String category,
            String material,
            Integer spec,
            String length,
            List<MatrixCell> cells
    ) {
    }

    /** 单元格: 命中的品牌版本上的价格。 */
    public record MatrixCell(
            String brandName,
            BigDecimal price,
            String priceStatus,
            Long listId,
            Long supplierId,
            String supplierName,
            LocalDateTime releasedAt,
            String reason
    ) {
    }
}
