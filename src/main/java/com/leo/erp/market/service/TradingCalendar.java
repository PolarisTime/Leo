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
 *
 * <p>向后取价一律逐日顺延：{@link #next(LocalDate)} / {@link #after(LocalDate, int)} 只把
 * 有行情的日期当作交易日计数，中间的休市/无行情日照常跳过；顺延到日历末端仍不足所需个数时
 * 返回空，调用方据此给出「行情尚未发布」而不是报错。</p>
 *
 * <p>向前取价与向后对称：{@link #previous(LocalDate)} / {@link #before(LocalDate, int)} 只把
 * 有行情的日期当作交易日计数，回溯到日历起点仍不足所需个数时返回空，调用方据此给出「可用行情
 * 范围内找不到更早的交易日」(参数错误 422) —— 与「行情尚未发布」的可重试语义区分开。</p>
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

    /** 严格早于给定日期的最近交易日（= {@link #before(LocalDate, int)} 取 1 个）。 */
    Optional<LocalDate> previous(LocalDate date) {
        return before(date, 1);
    }

    /**
     * 严格早于给定日期的第 {@code n} 个交易日；{@code n < 1} 视为 1（与 {@link #after(LocalDate, int)} 对称）。
     *
     * <p>逐日向前回溯：中间仍是休市/无行情（不进入交易日集合）就继续往前走，直到凑满 {@code n} 个；
     * 回溯到日历起点仍不足时返回空 —— 由调用方判定为「可用行情范围内找不到更早的交易日」(422)，
     * 而不是猜一个日期。</p>
     */
    Optional<LocalDate> before(LocalDate date, int n) {
        if (date == null) {
            return Optional.empty();
        }
        int remaining = Math.max(n, 1);
        Optional<LocalDate> cursor = Optional.of(date);
        while (remaining > 0) {
            cursor = cursor.flatMap(this::nearestBefore);
            if (cursor.isEmpty()) {
                return Optional.empty();
            }
            remaining--;
        }
        return cursor;
    }

    /** 单步回溯：严格早于给定日期的最近交易日（{@link #previous} / {@link #before} 的唯一扫描实现）。 */
    private Optional<LocalDate> nearestBefore(LocalDate date) {
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

    /** 严格晚于给定日期的最近交易日（= 从次日开始逐日向后顺延，跳过休市/无行情日）。 */
    Optional<LocalDate> next(LocalDate date) {
        if (date == null) {
            return Optional.empty();
        }
        for (LocalDate day : tradingDays) {
            if (day.isAfter(date)) {
                return Optional.of(day);
            }
        }
        return Optional.empty();
    }

    /**
     * 严格晚于给定日期的第 {@code n} 个交易日；{@code n < 1} 视为 1。
     *
     * <p>逐日向后顺延：后面仍是休市/无行情（不进入交易日集合）就继续顺延到下一天，直到凑满
     * {@code n} 个；顺延到日历末端仍不足时返回空 —— 由调用方判定为「行情尚未发布」，不再当作
     * 参数错误。</p>
     */
    Optional<LocalDate> after(LocalDate date, int n) {
        if (date == null) {
            return Optional.empty();
        }
        int remaining = Math.max(n, 1);
        Optional<LocalDate> cursor = Optional.of(date);
        while (remaining > 0) {
            cursor = cursor.flatMap(this::next);
            if (cursor.isEmpty()) {
                return Optional.empty();
            }
            remaining--;
        }
        return cursor;
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
