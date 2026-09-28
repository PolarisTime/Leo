package com.leo.erp.market.pricelist.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * 价格表整表/选区加减请求。
 * <p>{@code itemIds} 省略或为空 = 整表; 显式指定时不报价条目计入 skippedCount。</p>
 */
public record PriceAdjustmentRequest(
        @NotNull(message = "加减方向不能为空") String mode,
        @NotNull(message = "加减金额不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "加减金额必须大于 0") BigDecimal amount,
        List<Long> itemIds
) {
}
