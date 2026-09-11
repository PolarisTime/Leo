package com.leo.erp.market.web.dto;

import java.time.LocalDate;

/**
 * 行情补数受理响应(后台异步执行)。
 */
public record SteelQuoteBackfillResponse(LocalDate from, LocalDate to, int days, boolean accepted) {
}
