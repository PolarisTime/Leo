package com.leo.erp.market.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 商品↔行情匹配结果响应。
 */
public record MaterialPriceMatchResponse(
        Long materialId,
        String materialCode,
        String brand,
        String material,
        String category,
        String spec,
        String length,
        String status,
        String factory,
        String matchedSpec,
        boolean singleSpecPrice,
        BigDecimal basePrice,
        BigDecimal price,
        String priceType,
        String changeVal,
        String remark,
        LocalDate quoteDate,
        String period
) {
}
