package com.leo.erp.auth.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserAccountAdminResponse(
        Long id,
        String loginName,
        String userName,
        String mobile,
        Long departmentId,
        String departmentName,
        List<String> roleNames,
        String dataScope,
        String permissionSummary,
        LocalDateTime lastLoginDate,
        String status,
        String remark,
        Boolean totpEnabled
) {
}
