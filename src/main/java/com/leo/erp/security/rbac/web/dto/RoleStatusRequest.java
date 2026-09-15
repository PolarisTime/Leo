package com.leo.erp.security.rbac.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleStatusRequest(
        @NotBlank(message = "状态不能为空")
        String status
) {
}
