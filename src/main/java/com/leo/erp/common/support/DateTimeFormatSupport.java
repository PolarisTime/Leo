package com.leo.erp.common.support;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public final class DateTimeFormatSupport {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

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
}
