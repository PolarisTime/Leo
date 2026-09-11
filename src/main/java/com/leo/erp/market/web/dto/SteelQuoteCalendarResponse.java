package com.leo.erp.market.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 行情日历: 某日期可用的时段列表(用于前端日历点位提示)。
 */
public record SteelQuoteCalendarResponse(LocalDate quoteDate, List<String> periods) {
}
