package com.leo.erp.common.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DateTimeFormatSupportTest {

    @Test
    void shouldFormatLocalDateTime() {
        LocalDateTime dateTime = LocalDateTime.of(2026, 1, 15, 10, 30, 45);
        String formatted = DateTimeFormatSupport.format(dateTime);
        assertThat(formatted).isEqualTo("2026-01-15 10:30:45");
    }

    @Test
    void shouldReturnEmptyStringForNullLocalDateTime() {
        assertThat(DateTimeFormatSupport.format((LocalDateTime) null)).isEmpty();
    }

    @Test
    void shouldFormatOffsetDateTime() {
        OffsetDateTime dateTime = OffsetDateTime.of(2026, 1, 15, 10, 30, 45, 0, ZoneOffset.ofHours(8));
        String formatted = DateTimeFormatSupport.format(dateTime);
        assertThat(formatted).isEqualTo("2026-01-15 10:30:45");
    }

    @Test
    void shouldReturnEmptyStringForNullOffsetDateTime() {
        assertThat(DateTimeFormatSupport.format((OffsetDateTime) null)).isEmpty();
    }

    @Test
    void shouldReturnCurrentDateTimeFormatted() {
        String now = DateTimeFormatSupport.now();
        assertThat(now).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    void shouldHaveCorrectPattern() {
        assertThat(DateTimeFormatSupport.DATE_TIME_PATTERN).isEqualTo("yyyy-MM-dd HH:mm:ss");
    }

    @Test
    void shouldHaveFormatter() {
        assertThat(DateTimeFormatSupport.DATE_TIME_FORMATTER).isNotNull();
    }
}
