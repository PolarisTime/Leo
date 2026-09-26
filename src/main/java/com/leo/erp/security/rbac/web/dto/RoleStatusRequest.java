package com.leo.erp.security.rbac.web.dto;

import com.leo.erp.common.support.ValidationMessages;
import jakarta.validation.constraints.NotBlank;

public record RoleStatusRequest(
        @NotBlank(message = ValidationMessages.STATUS_REQUIRED)
        String status
) {
}
