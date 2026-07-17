package com.leo.erp.system.generalsetting.web.dto;

public record GeneralSettingResponse(
        Long id,
        String settingCode,
        String settingName,
        String settingGroup,
        String settingValue,
        String status,
        String remark
) {
}
