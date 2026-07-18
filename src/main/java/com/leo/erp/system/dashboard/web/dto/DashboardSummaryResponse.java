package com.leo.erp.system.dashboard.web.dto;

import java.time.LocalDateTime;

public record DashboardSummaryResponse(
        String appName,
        String companyName,
        String userName,
        String loginName,
        long visibleMenuCount,
        long moduleCount,
        long activeSessionCount,
        LocalDateTime lastLoginAt,
        LocalDateTime serverTime,
        long materialCount,
        long supplierCount,
        long customerCount
) {
}
