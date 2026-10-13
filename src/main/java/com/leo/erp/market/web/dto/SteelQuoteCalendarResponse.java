package com.leo.erp.market.web.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 行情日历: 某日期的可用时段列表与各时段行数(用于前端覆盖矩阵)。
 *
 * <p>{@code periods} 与 {@code periodRows} 都以**已入库的行情明细**为准（不是文章表）：
 * 矩阵上的「已同步」必须等同于「这一天这个时段真的能取到价格」，行数也必须是实际入库行数。</p>
 *
 * @param quoteDate  日期
 * @param periods    该日有行情(已入库行情明细)的时段列表; 休市日为空
 * @param periodRows 各时段实际入库的行情行数
 * @param closed     该日站点未发布行情(休市/节假日): 覆盖矩阵据此显示「休市」而不是「缺失」。
 *                   该结论由同步与补数过程判定并落库, 前端无法自行推算 —— 法定节假日与调休
 *                   并不服从「周六周日无行情」的直觉。
 */
public record SteelQuoteCalendarResponse(LocalDate quoteDate, List<String> periods,
                                         Map<String, Integer> periodRows, boolean closed) {

    /** 有行情的日期。 */
    public static SteelQuoteCalendarResponse of(LocalDate quoteDate, List<String> periods,
                                                Map<String, Integer> periodRows) {
        return new SteelQuoteCalendarResponse(quoteDate, periods, periodRows, false);
    }

    /** 休市日: 站点该日未发布行情。 */
    public static SteelQuoteCalendarResponse closed(LocalDate quoteDate) {
        return new SteelQuoteCalendarResponse(quoteDate, List.of(), Map.of(), true);
    }
}
