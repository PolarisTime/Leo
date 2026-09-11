package com.leo.erp.market.web.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 行情补数任务状态。
 */
public record SteelQuoteBackfillStatusResponse(
        boolean running,
        LocalDate from,
        LocalDate to,
        Instant startedAt,
        Instant finishedAt,
        int syncedDays,
        int failedDays,
        int totalRows
) {
}
