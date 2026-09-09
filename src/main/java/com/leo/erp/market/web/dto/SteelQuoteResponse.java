package com.leo.erp.market.web.dto;

import com.leo.erp.market.domain.entity.SteelQuote;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 行查明细分页响应。
 */
public record SteelQuoteResponse(
        Long id,
        String market,
        LocalDate quoteDate,
        String period,
        String breed,
        String spec,
        String material,
        String factory,
        BigDecimal price,
        String changeVal,
        String remark,
        LocalDateTime scrapedAt
) {

    public static SteelQuoteResponse from(SteelQuote quote) {
        return new SteelQuoteResponse(quote.getId(), quote.getMarket(), quote.getQuoteDate(), quote.getPeriod(),
                quote.getBreed(), quote.getSpec(), quote.getMaterial(), quote.getFactory(), quote.getPrice(),
                quote.getChangeVal(), quote.getRemark(), quote.getScrapedAt());
    }
}
