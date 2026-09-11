package com.leo.erp.market.web.dto;

import java.time.LocalDate;

/**
 * 行情补数请求: 指定最近 days 天, 或 from/to 区间。
 */
public record SteelQuoteBackfillRequest(Integer days, LocalDate from, LocalDate to) {
}
