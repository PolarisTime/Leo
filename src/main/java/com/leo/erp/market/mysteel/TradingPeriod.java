package com.leo.erp.market.mysteel;

import java.time.LocalTime;

/**
 * 行情时段判定: 11点前=上午, 11-14点=中午, 14点起=下午(与既有脚本口径一致)。
 */
public enum TradingPeriod {

    MORNING("上午"),
    NOON("中午"),
    AFTERNOON("下午");

    private static final LocalTime NOON_START = LocalTime.of(11, 0);
    private static final LocalTime AFTERNOON_START = LocalTime.of(14, 0);

    private final String label;

    TradingPeriod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static TradingPeriod from(LocalTime time) {
        if (time.isBefore(NOON_START)) {
            return MORNING;
        }
        return time.isBefore(AFTERNOON_START) ? NOON : AFTERNOON;
    }

    public static TradingPeriod fromLabel(String label) {
        for (TradingPeriod period : values()) {
            if (period.label.equals(label)) {
                return period;
            }
        }
        throw new IllegalArgumentException("未知时段: " + label);
    }
}
