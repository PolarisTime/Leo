package com.leo.erp.auth.web.dto;

import com.leo.erp.auth.domain.enums.UserStatus;

import java.time.LocalDateTime;

/** 管理员视角的用户账号表示。 */
public record UserAccountResponse(
        Long id,
        String loginName,
        String userName,
        String mobile,
        UserStatus status,
        LocalDateTime lastLoginDate,
        String remark
) {
}
