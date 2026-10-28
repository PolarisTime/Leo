package com.leo.erp.system.operationlog.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 操作日志概览统计响应。
 *
 * <p>字段名与前端 {@code OperationLogSummary} 契约逐字一致; 无数据时计数为 0、数组为空。
 * {@code byDay} 只返回有数据的日期(前端负责按 7 天补零), 避免后端为无数据日期造行。</p>
 *
 * @param total            总条数
 * @param successCount     成功条数
 * @param failedCount      失败条数
 * @param avgDurationMs    平均耗时(毫秒, 四舍五入); 无耗时数据时为 0
 * @param byDay            按天聚合(升序)
 * @param topFailedModules 失败次数最多的模块前 5
 */
public record OperationLogSummaryResponse(
        long total,
        long successCount,
        long failedCount,
        long avgDurationMs,
        List<DailyCount> byDay,
        List<FailedModule> topFailedModules
) {

    /** 单日聚合: {@code date} 为 ISO 日期(yyyy-MM-dd)。 */
    public record DailyCount(LocalDate date, long total, long failed) {
    }

    /** 失败模块计数。 */
    public record FailedModule(String moduleName, long count) {
    }
}
