package com.leo.erp.market.pricelist.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 比价单据的"报价时刻"({@code quoteAsOf})推导。
 *
 * <p>口径: {@code mk_quote_sheet.order_date}(报价日期) + {@code ref_period} 对应的时刻。
 * 取版必须用该时刻而不是当前时间, 否则历史单据的现货价会随新版本发布而漂移。</p>
 *
 * <p>解析规则(按优先级):</p>
 * <ol>
 *   <li>{@code ref_period} 中含 {@code HH:mm} 或 {@code HH:mm:ss} → 取该时刻(当日);</li>
 *   <li>只含整点描述(如 {@code 9时}/{@code 9点}) → 取该整点;</li>
 *   <li>无法解析(如 {@code 上午}) → 取当日 {@code 23:59:59}, 即"当日内最晚的版本"。
 *       该选择对"上午报单 + 当日又发布新版本"的场景会带上新版本, 属已知保守取舍:
 *       宁可取到当日最新价, 也不因时段语义不明而回退到昨天。</li>
 * </ol>
 */
public final class QuoteAsOf {

    private static final Pattern TIME = Pattern.compile("(\\d{1,2})\\s*[:：]\\s*(\\d{1,2})(?:\\s*[:：]\\s*(\\d{1,2}))?");
    private static final Pattern HOUR = Pattern.compile("(\\d{1,2})\\s*(?:时|点)(?!\\s*\\d)");
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    private QuoteAsOf() {
    }

    /** 由报价日期 + 参照时段推导报价时刻; 日期为空时返回 null(调用方回退当前时间)。 */
    public static LocalDateTime of(LocalDate orderDate, String refPeriod) {
        if (orderDate == null) {
            return null;
        }
        return LocalDateTime.of(orderDate, resolveTime(refPeriod));
    }

    /** 参照时段解析为当日时刻, 无法解析时返回当日 23:59:59。 */
    public static LocalTime resolveTime(String refPeriod) {
        if (refPeriod == null || refPeriod.isBlank()) {
            return END_OF_DAY;
        }
        Matcher time = TIME.matcher(refPeriod);
        if (time.find()) {
            int hour = clamp(Integer.parseInt(time.group(1)), 0, 23);
            int minute = clamp(Integer.parseInt(time.group(2)), 0, 59);
            int second = time.group(3) == null ? 0 : clamp(Integer.parseInt(time.group(3)), 0, 59);
            return LocalTime.of(hour, minute, second);
        }
        Matcher hour = HOUR.matcher(refPeriod);
        if (hour.find()) {
            return LocalTime.of(clamp(Integer.parseInt(hour.group(1)), 0, 23), 0, 0);
        }
        return END_OF_DAY;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
