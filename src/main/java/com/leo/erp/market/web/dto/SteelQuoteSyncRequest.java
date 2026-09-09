package com.leo.erp.market.web.dto;

import java.time.LocalDate;

/**
 * 手动同步行情请求。
 */
public record SteelQuoteSyncRequest(LocalDate date) {

    public SteelQuoteSyncRequest {
        if (date == null) {
            date = LocalDate.now();
        }
    }
}
