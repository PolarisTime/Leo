package com.leo.erp.common.api;

import java.time.LocalDate;

/**
 * 通用分页查询过滤参数，用于替代 Service 方法中过多的筛选参数。
 * 各 Service 可按需使用其中的字段，未使用的字段保持 null 即可。
 */
public record PageFilter(
        String keyword,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        String name,
        String projectName,
        String businessType,
        String moduleName,
        String actionType,
        String resultStatus,
        String signStatus,
        String usageScope,
        Long recordId,
        Long userId
) {
    public PageFilter {
        // defaults via compact constructor — no-op, all nullable
    }

    /** 最常用的四字段构造 */
    public static PageFilter of(String keyword, String status, LocalDate startDate, LocalDate endDate) {
        return new PageFilter(keyword, status, startDate, endDate,
                null, null, null, null, null, null, null, null, null, null);
    }

    /** 带一个名称过滤 */
    public static PageFilter of(String keyword, String name, String status, LocalDate startDate, LocalDate endDate) {
        return new PageFilter(keyword, status, startDate, endDate,
                name, null, null, null, null, null, null, null, null, null);
    }
}
