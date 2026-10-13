package com.leo.erp.common.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public final class DateTimeFormatSupport {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    /**
     * 中文日期展示口径：{@code X年X月X日}（月/日个位数补零）。
     *
     * <p>只用于拼给用户看的中文文本（提示/说明/校验消息）；接口数据、日志与机器字段仍用 ISO，
     * 避免展示口径污染契约。</p>
     */
    public static final String DISPLAY_DATE_PATTERN = "yyyy年MM月dd日";
    public static final DateTimeFormatter DISPLAY_DATE_FORMATTER = DateTimeFormatter.ofPattern(DISPLAY_DATE_PATTERN);

    private DateTimeFormatSupport() {
    }

    public static String now() {
        return format(LocalDateTime.now());
    }

    public static String format(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_FORMATTER);
    }

    public static String format(OffsetDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_FORMATTER);
    }

    /** 把日期格式化为界面展示口径（如 {@code 2026年10月13日}）；空值返回空串。 */
    public static String displayDate(LocalDate value) {
        return value == null ? "" : value.format(DISPLAY_DATE_FORMATTER);
    }

    /** 把日期时间格式化为界面展示口径（如 {@code 2026年10月13日 08:05:00}）；空值返回空串。 */
    public static String displayDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DISPLAY_DATE_TIME_FORMATTER);
    }

    private static final DateTimeFormatter DISPLAY_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(DISPLAY_DATE_PATTERN + " HH:mm:ss");
}
