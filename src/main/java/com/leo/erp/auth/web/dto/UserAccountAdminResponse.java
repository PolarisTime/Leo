package com.leo.erp.auth.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.leo.erp.common.support.PhoneMaskSerializer;

import java.time.LocalDateTime;
public record UserAccountAdminResponse(
        Long id,
        String loginName,
        String userName,
        @JsonSerialize(using = PhoneMaskSerializer.class) String mobile,
        Long departmentId,
        String departmentName,
        LocalDateTime lastLoginDate,
        String status,
        String remark
) {
}
