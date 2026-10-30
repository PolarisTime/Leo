package com.leo.erp.common.web.dto;

import com.leo.erp.common.support.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StatusUpdateRequest(
        @NotBlank(message = ValidationMessages.STATUS_REQUIRED)
        // 各模块 status 列统一为 varchar(16)
        @Size(max = 16, message = "状态长度不能超过16个字符")
        String status
) {
}
