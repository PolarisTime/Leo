package com.leo.erp.system.operationlog.service;

import java.time.LocalDate;

/**
 * 操作日志概览统计的筛选条件(与分页接口共用同一批筛选语义的子集)。
 *
 * <p>日期为闭区间; 不传时默认最近 7 天, 最长 31 天。</p>
 */
public record OperationLogSummaryFilter(
        LocalDate startDate,
        LocalDate endDate,
        String moduleName,
        String actionType,
        String resultStatus,
        String category,
        String operatorName
) {
}
