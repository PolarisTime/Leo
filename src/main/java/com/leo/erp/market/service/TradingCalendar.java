package com.leo.erp.market.service;

import com.leo.erp.master.api.ProjectPricingBasisQuery;
import com.leo.erp.market.web.dto.SteelQuoteCalendarResponse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 取价用的交易日历（内存视图）。
 *
 * <p>「有行情交易日」= 该日存在至少一个时段的行情（日历行的 {@code periods} 非空）。
 * 休市日与「无行情」在结算口径上同等处理：都不可作为取价日，需要回退。</p>
 *
 * <p>判定只走行情日历（其休市结论由同步/补数过程落库），前端与业务代码都不要自行推算
 * 「周末无行情」——法定节假日与调休并不服从这个直觉。</p>
 */
final class TradingCalendar {

    private final List<LocalDate> tradingDays;
    private final Map<LocalDate, List<String>> periodsByDate;

    private TradingCalendar(List<LocalDate> tradingDays, Map<LocalDate, List<String>> periodsByDate) {
        this.tradingDays = tradingDays;
        this.periodsByDate = periodsByDate;
    }

    /** 从行情日历构建；只有有行情的日期进入交易日集合。 */
    static TradingCalendar of(List<SteelQuoteCalendarResponse> calendar) {
        List<LocalDate> days = new ArrayList<>();
        Map<LocalDate, List<String>> periodsByDate = new LinkedHashMap<>();
        for (SteelQuoteCalendarResponse row : calendar) {
            List<String> periods = row.periods() == null ? List.of() : row.periods();
            if (periods.isEmpty()) {
                continue;
            }
            days.add(row.quoteDate());
            periodsByDate.put(row.quoteDate(), sortPeriods(periods));
        }
        days.sort(Comparator.naturalOrder());
        return new TradingCalendar(List.copyOf(days), Map.copyOf(periodsByDate));
    }

    /** 时段按交易时段先后（上午 → 中午 → 下午）排序，避免依赖文章抓取顺序。 */
    private static List<String> sortPeriods(List<String> periods) {
        return periods.stream()
                .distinct()
                .sorted(Comparator.comparingInt(ProjectPricingBasisQuery.QuotePeriod::indexOfLabel))
                .toList();
    }

    boolean isTradingDay(LocalDate date) {
        return date != null && periodsByDate.containsKey(date);
    }

    /** 严格早于给定日期的最近交易日。 */
    Optional<LocalDate> previous(LocalDate date) {
        if (date == null) {
            return Optional.empty();
        }
        LocalDate found = null;
        for (LocalDate day : tradingDays) {
            if (day.isBefore(date)) {
                found = day;
            } else {
                break;
            }
        }
        return Optional.ofNullable(found);
    }

    /** 严格晚于给定日期的第 {@code n} 个交易日；{@code n < 1} 视为 1。 */
    Optional<LocalDate> after(LocalDate date, int n) {
        if (date == null) {
            return Optional.empty();
        }
        int remaining = Math.max(n, 1);
        for (LocalDate day : tradingDays) {
            if (!day.isAfter(date)) {
                continue;
            }
            remaining--;
            if (remaining == 0) {
                return Optional.of(day);
            }
        }
        return Optional.empty();
    }

    /** 该日可用时段（交易日历内；非交易日返回空列表）。 */
    List<String> periods(LocalDate date) {
        if (date == null) {
            return List.of();
        }
        return periodsByDate.getOrDefault(date, List.of());
    }

    /** 日历窗口内的最早交易日，用于「窗口不够用」时的中文提示。 */
    Optional<LocalDate> earliest() {
        return tradingDays.isEmpty() ? Optional.empty() : Optional.of(tradingDays.get(0));
    }

    /** 日历窗口内的最晚交易日。 */
    Optional<LocalDate> latest() {
        return tradingDays.isEmpty() ? Optional.empty() : Optional.of(tradingDays.get(tradingDays.size() - 1));
    }
}
