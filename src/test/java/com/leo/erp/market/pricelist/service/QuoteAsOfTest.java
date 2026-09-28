package com.leo.erp.market.pricelist.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 报价时刻(quoteAsOf)推导的边界口径。
 */
class QuoteAsOfTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 28);

    @Test
    void parsesHourMinuteFromPeriod() {
        assertThat(QuoteAsOf.of(DATE, "09:30")).isEqualTo(LocalDateTime.of(DATE, LocalTime.of(9, 30)));
        assertThat(QuoteAsOf.of(DATE, "9：30")).isEqualTo(LocalDateTime.of(DATE, LocalTime.of(9, 30)));
        assertThat(QuoteAsOf.of(DATE, "上午 10:15 报价")).isEqualTo(LocalDateTime.of(DATE, LocalTime.of(10, 15)));
        assertThat(QuoteAsOf.of(DATE, "14:35:20")).isEqualTo(LocalDateTime.of(DATE, LocalTime.of(14, 35, 20)));
    }

    @Test
    void parsesChineseHour() {
        assertThat(QuoteAsOf.of(DATE, "9时")).isEqualTo(LocalDateTime.of(DATE, LocalTime.of(9, 0)));
        assertThat(QuoteAsOf.of(DATE, "9点")).isEqualTo(LocalDateTime.of(DATE, LocalTime.of(9, 0)));
    }

    /** 无法解析成时刻(如"上午")时保守取当日 23:59:59, 即当日内最晚版本。 */
    @Test
    void fallsBackToEndOfDayWhenPeriodHasNoTime() {
        assertThat(QuoteAsOf.of(DATE, "上午")).isEqualTo(LocalDateTime.of(DATE, LocalTime.of(23, 59, 59)));
        assertThat(QuoteAsOf.of(DATE, null)).isEqualTo(LocalDateTime.of(DATE, LocalTime.of(23, 59, 59)));
        assertThat(QuoteAsOf.of(DATE, "  ")).isEqualTo(LocalDateTime.of(DATE, LocalTime.of(23, 59, 59)));
    }

    @Test
    void clampsOutOfRangeValues() {
        assertThat(QuoteAsOf.of(DATE, "25:99")).isEqualTo(LocalDateTime.of(DATE, LocalTime.of(23, 59)));
    }

    @Test
    void returnsNullWhenDateMissing() {
        assertThat(QuoteAsOf.of(null, "09:30")).isNull();
    }
}
