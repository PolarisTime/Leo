package com.leo.erp.common.web.dto;

import com.leo.erp.common.support.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StatusUpdateRequest(
        @NotBlank(message = ValidationMessages.STATUS_REQUIRED)
        @Size(max = 32, message = "状态长度不能超过32个字符")
        String status
) {
}
