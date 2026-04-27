package com.leo.erp.system.norule.web.dto;

public record GeneralSettingResponse(
        Long id,
        String settingCode,
        String settingName,
        String billName,
        String prefix,
        String dateRule,
        Integer serialLength,
        String resetRule,
        String sampleNo,
        String status,
        String remark,
        String ruleType,
        String moduleKey
) {
}
