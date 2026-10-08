package com.leo.erp.market.web.dto;

import com.leo.erp.market.service.SteelQuoteSyncService.BackfillFailure;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 行情补数任务状态。
 *
 * @param running      是否进行中
 * @param from         起始日期
 * @param to           结束日期
 * @param startedAt    开始时间
 * @param finishedAt   结束时间(未结束时为 null)
 * @param syncedDays   成功同步天数
 * @param skippedDays  跳过天数(站点该日未发布行情: 休市/节假日)
 * @param failedDays   失败天数
 * @param totalRows    入库总行数
 * @param skippedDates 跳过的日期明细(便于核对是休市日还是漏数)
 * @param failures     失败明细
 */
public record SteelQuoteBackfillStatusResponse(
        boolean running,
        LocalDate from,
        LocalDate to,
        Instant startedAt,
        Instant finishedAt,
        int syncedDays,
        int skippedDays,
        int failedDays,
        int totalRows,
        List<LocalDate> skippedDates,
        List<BackfillFailure> failures
) {
}
