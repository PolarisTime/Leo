package com.leo.erp.market.pricelist.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对照矩阵(只读投影)。
 *
 * <p>已取消版本语义(契约 4.6 修订 R2): 每个 (供应商, 品牌) 投影其<b>当前</b>未删除价格表
 * (一个 (供应商, 品牌) 至多一张)。{@code asOf} 仅为兼容保留并回显, 不再参与任何选版;
 * 列/格的 {@code releasedAt} 兼容保留, 实际填价格表的 {@code updated_at}。</p>
 */
public record SupplierPriceMatrixResponse(
        LocalDateTime asOf,
        List<MatrixColumn> columns,
        List<MatrixRow> rows
) {

    /** 矩阵列 = 一个 (供应商, 品牌) 的当前价格表。 */
    public record MatrixColumn(
            Long supplierId,
            String supplierName,
            String brandName,
            Long listId,
            /** 兼容保留: 填价格表 {@code updated_at}。 */
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

    /** 单元格: 命中的品牌价格表上的价格。 */
    public record MatrixCell(
            String brandName,
            BigDecimal price,
            String priceStatus,
            Long listId,
            Long supplierId,
            String supplierName,
            /** 兼容保留: 填价格表 {@code updated_at}。 */
            LocalDateTime releasedAt,
            String reason
    ) {
    }
}
