package com.leo.erp.market.pricelist.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 价格表加减留痕响应(调整历史)。 */
public record PriceAdjustmentHistoryResponse(
        Long id,
        String mode,
        BigDecimal amount,
        Integer itemCount,
        Long createdBy,
        String createdName,
        LocalDateTime createdAt
) {
}
