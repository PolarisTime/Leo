package com.leo.erp.market.service;

import com.leo.erp.market.web.dto.SteelQuoteCalendarResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 交易日历（{@link TradingCalendar}）的单元测试。
 *
 * <p>重点锁定「逐日向后顺延」这一条：休市/无行情日不进入交易日集合，{@code next}/{@code after}
 * 只跨过它们计数；顺延到日历末端仍不足时返回空（由调用方判定为「行情尚未发布」）。</p>
 */
class TradingCalendarTest {

    /** 2026-03-02 是周一。 */
    private static final LocalDate MON = LocalDate.of(2026, 3, 2);
    private static final LocalDate TUE = MON.plusDays(1);
    private static final LocalDate WED = MON.plusDays(2);
    private static final LocalDate THU = MON.plusDays(3);
    private static final LocalDate FRI = MON.plusDays(4);
    private static final LocalDate SAT = MON.plusDays(5);
    private static final LocalDate SUN = MON.plusDays(6);
    private static final LocalDate NEXT_MON = MON.plusDays(7);
    private static final LocalDate NEXT_TUE = MON.plusDays(8);

    @Test
    void 只有有行情的日期进入交易日集合() {
        TradingCalendar calendar = TradingCalendar.of(List.of(
                open(MON, "上午", "下午"),
                closed(TUE),
                open(WED, "上午")));

        assertThat(calendar.isTradingDay(MON)).isTrue();
        assertThat(calendar.isTradingDay(TUE)).as("休市日不是交易日").isFalse();
        assertThat(calendar.isTradingDay(THU)).as("日历里没有的日期也不是交易日").isFalse();
        assertThat(calendar.isTradingDay(null)).isFalse();
        assertThat(calendar.earliest()).contains(MON);
        assertThat(calendar.latest()).contains(WED);
    }

    @Test
    void 时段按交易时段先后排序且去重() {
        TradingCalendar calendar = TradingCalendar.of(List.of(
                open(MON, "下午", "上午", "中午", "上午")));

        assertThat(calendar.periods(MON)).containsExactly("上午", "中午", "下午");
        assertThat(calendar.periods(TUE)).isEmpty();
        assertThat(calendar.periods(null)).isEmpty();
    }

    @Test
    void next逐日顺延跳过休市与周末() {
        TradingCalendar calendar = holidayThenOpen();

        assertThat(calendar.next(MON)).contains(FRI);
        assertThat(calendar.next(FRI)).contains(NEXT_MON);
        assertThat(calendar.next(SUN)).as("周日之后是下周一").contains(NEXT_MON);
        assertThat(calendar.next(NEXT_TUE)).as("日历末端之后没有下一个交易日").isEmpty();
        assertThat(calendar.next(null)).isEmpty();
    }

    @Test
    void after逐日顺延计数且在不足时返回空() {
        TradingCalendar calendar = holidayThenOpen();

        assertThat(calendar.after(MON, 1)).contains(FRI);
        assertThat(calendar.after(MON, 2)).contains(NEXT_MON);
        assertThat(calendar.after(MON, 3)).contains(NEXT_TUE);
        assertThat(calendar.after(MON, 4)).as("顺延到末端仍不足 4 个交易日").isEmpty();
        assertThat(calendar.after(NEXT_TUE, 1)).isEmpty();
        assertThat(calendar.after(null, 1)).isEmpty();
    }

    @Test
    void after的交易日数小于一时按一个交易日处理() {
        TradingCalendar calendar = holidayThenOpen();

        assertThat(calendar.after(MON, 0)).contains(FRI);
        assertThat(calendar.after(MON, -3)).contains(FRI);
    }

    @Test
    void previous只取严格早于给定日期的最近交易日() {
        TradingCalendar calendar = holidayThenOpen();

        assertThat(calendar.previous(NEXT_TUE)).contains(NEXT_MON);
        assertThat(calendar.previous(MON)).as("周一之前没有交易日").isEmpty();
        assertThat(calendar.previous(null)).isEmpty();
    }

    @Test
    void 空日历的所有查询都返回空() {
        TradingCalendar calendar = TradingCalendar.of(List.of());

        assertThat(calendar.earliest()).isEmpty();
        assertThat(calendar.latest()).isEmpty();
        assertThat(calendar.next(MON)).isEmpty();
        assertThat(calendar.after(MON, 1)).isEmpty();
        assertThat(calendar.previous(MON)).isEmpty();
    }

    /** 长假日历：周一有行情，周二/周三/周四连续休市，周五与下周一/下周二有行情。 */
    private static TradingCalendar holidayThenOpen() {
        return TradingCalendar.of(List.of(
                open(MON, "上午"),
                closed(TUE),
                closed(WED),
                closed(THU),
                open(FRI, "上午"),
                open(NEXT_MON, "上午"),
                open(NEXT_TUE, "上午")));
    }

    private static SteelQuoteCalendarResponse open(LocalDate date, String... periods) {
        Map<String, Integer> periodRows = new LinkedHashMap<>();
        for (String period : periods) {
            periodRows.put(period, 1);
        }
        return SteelQuoteCalendarResponse.of(date, List.of(periods), periodRows);
    }

    private static SteelQuoteCalendarResponse closed(LocalDate date) {
        return SteelQuoteCalendarResponse.closed(date);
    }
}
