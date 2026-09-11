package com.leo.erp.market.web.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 行情日历: 某日期可用的时段列表与各时段行数(用于前端覆盖矩阵)。
 */
public record SteelQuoteCalendarResponse(LocalDate quoteDate, List<String> periods,
                                         Map<String, Integer> periodRows) {
}
