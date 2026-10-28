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
 * @param tradingDay 该日是否交易日; 来自交易日例外日历(法定节假日/调休上班)。为 {@code null}
 *                   表示没有例外记录, 前端退回「周末即休市」的兜底规则。调休上班的周六日靠这个
 *                   字段才不会被误判成休市。
 */
public record SteelQuoteCalendarResponse(LocalDate quoteDate, List<String> periods,
                                         Map<String, Integer> periodRows, boolean closed,
                                         Boolean tradingDay) {

    /** 有行情的日期。 */
    public static SteelQuoteCalendarResponse of(LocalDate quoteDate, List<String> periods,
                                                Map<String, Integer> periodRows) {
        return new SteelQuoteCalendarResponse(quoteDate, periods, periodRows, false, Boolean.TRUE);
    }

    /** 休市日: 站点该日未发布行情。 */
    public static SteelQuoteCalendarResponse closed(LocalDate quoteDate) {
        return new SteelQuoteCalendarResponse(quoteDate, List.of(), Map.of(), true, Boolean.FALSE);
    }

    /**
     * 法定节假日休市(尚未同步过、也没有站点记录): 与 {@link #closed} 同义, 只是来源是年度日历。
     */
    public static SteelQuoteCalendarResponse holiday(LocalDate quoteDate) {
        return closed(quoteDate);
    }

    /**
     * 调休上班日: 没有例外记录之外仍要显式告诉前端"这天是交易日"。
     * 没有行情时 periods 为空, 但 closed=false + tradingDay=true 让矩阵显示「缺失(可同步)」。
     */
    public static SteelQuoteCalendarResponse workingDay(LocalDate quoteDate) {
        return new SteelQuoteCalendarResponse(quoteDate, List.of(), Map.of(), false, Boolean.TRUE);
    }
}
