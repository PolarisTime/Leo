package com.leo.erp.market.mysteel;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradingPeriodTest {

    @Test
    void before11am_isMorning() {
        assertThat(TradingPeriod.from(LocalTime.of(0, 0))).isEqualTo(TradingPeriod.MORNING);
        assertThat(TradingPeriod.from(LocalTime.of(9, 30))).isEqualTo(TradingPeriod.MORNING);
        assertThat(TradingPeriod.from(LocalTime.of(10, 59))).isEqualTo(TradingPeriod.MORNING);
    }

    @Test
    void between11And14_isNoon() {
        assertThat(TradingPeriod.from(LocalTime.of(11, 0))).isEqualTo(TradingPeriod.NOON);
        assertThat(TradingPeriod.from(LocalTime.of(12, 0))).isEqualTo(TradingPeriod.NOON);
        assertThat(TradingPeriod.from(LocalTime.of(13, 59))).isEqualTo(TradingPeriod.NOON);
    }

    @Test
    void from14pm_isAfternoon() {
        assertThat(TradingPeriod.from(LocalTime.of(14, 0))).isEqualTo(TradingPeriod.AFTERNOON);
        assertThat(TradingPeriod.from(LocalTime.of(15, 40))).isEqualTo(TradingPeriod.AFTERNOON);
        assertThat(TradingPeriod.from(LocalTime.of(23, 59))).isEqualTo(TradingPeriod.AFTERNOON);
    }

    @Test
    void labelRoundTrip() {
        for (TradingPeriod period : TradingPeriod.values()) {
            assertThat(TradingPeriod.fromLabel(period.label())).isEqualTo(period);
        }
        assertThat(TradingPeriod.MORNING.label()).isEqualTo("上午");
        assertThat(TradingPeriod.NOON.label()).isEqualTo("中午");
        assertThat(TradingPeriod.AFTERNOON.label()).isEqualTo("下午");
    }

    @Test
    void unknownLabelRejected() {
        assertThatThrownBy(() -> TradingPeriod.fromLabel("晚上")).isInstanceOf(IllegalArgumentException.class);
    }
}
