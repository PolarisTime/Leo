package com.leo.erp.system.generalsetting.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GeneralSettingUpdateRequest(
        @NotBlank @Size(max = 64) String settingCode,
        @NotBlank @Size(max = 128) String settingName,
        @NotBlank @Size(max = 128) String settingGroup,
        @NotBlank @Size(max = 64) String settingValue,
        @Size(max = 16) String status,
        @Size(max = 255) String remark
) {
}
