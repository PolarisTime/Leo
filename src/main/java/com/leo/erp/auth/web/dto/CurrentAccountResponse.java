package com.leo.erp.auth.web.dto;

import java.time.LocalDateTime;

/** 当前唯一登录账号的可编辑资料。 */
public record CurrentAccountResponse(
        Long id,
        String loginName,
        String userName,
        String mobile,
        LocalDateTime lastLoginDate,
        String remark
) {
}
