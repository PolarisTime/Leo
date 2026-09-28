package com.leo.erp.market.pricelist.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 价格拉取结果: 写入/保留/跳过计数 + 未匹配行明细。 */
public record PricePullResponse(
        Long id,
        Long sheetId,
        LocalDateTime pulledAt,
        int filledCount,
        int preservedCount,
        int skippedCount,
        List<UnmatchedRow> unmatchedRows
) {

    /** 未匹配行: 说明为什么没有可用价格。 */
    public record UnmatchedRow(
            String category,
            String material,
            Integer spec,
            String length,
            String brandName,
            String reason
    ) {
    }

    /** 格式化条目(内部使用): 拉取时同时回写来源价格表信息。 */
    public record FilledCell(Long itemId, String brandName, BigDecimal price,
                             Long priceListId, LocalDateTime priceListReleasedAt) {
    }
}
