package com.leo.erp.market.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 手动同步行情请求。
 *
 * @param date    行情日期, 缺省为当天
 * @param periods 需要同步的时段(上午/中午/下午); 空表示全部时段
 */
public record SteelQuoteSyncRequest(LocalDate date, List<String> periods) {

    public SteelQuoteSyncRequest {
        if (date == null) {
            date = LocalDate.now();
        }
        periods = periods == null ? List.of() : List.copyOf(periods);
    }
}
