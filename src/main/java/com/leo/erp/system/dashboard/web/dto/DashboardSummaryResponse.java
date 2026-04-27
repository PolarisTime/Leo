package com.leo.erp.system.dashboard.web.dto;

import java.time.LocalDateTime;

public record DashboardSummaryResponse(
        String appName,
        String companyName,
        String userName,
        String loginName,
        String roleName,
        long visibleMenuCount,
        long moduleCount,
        long actionCount,
        long activeSessionCount,
        boolean totpEnabled,
        LocalDateTime lastLoginAt,
        LocalDateTime serverTime
) {
}
