package com.leo.erp.security.rbac.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UserRolesUpdateRequest(
        @NotNull(message = "角色集合不能为空")
        List<Long> roleIds
) {
}
