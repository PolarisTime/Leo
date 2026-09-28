package com.leo.erp.market.pricelist.web.dto;

import java.math.BigDecimal;
import java.util.List;

/** 价格表加减响应: 留痕ID + 受影响条目数 + 调整后价格(前端预览确认后回填)。 */
public record PriceAdjustmentResponse(
        Long adjustmentId,
        int affectedCount,
        int skippedCount,
        List<AdjustedItem> items
) {

    /** 调整后的条目价格。 */
    public record AdjustedItem(Long id, BigDecimal price) {
    }
}
