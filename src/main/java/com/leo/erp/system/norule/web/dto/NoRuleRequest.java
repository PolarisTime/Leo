package com.leo.erp.system.norule.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NoRuleRequest(
        @NotBlank String settingCode,
        @NotBlank String settingName,
        @NotBlank String billName,
        @NotBlank String prefix,
        @NotBlank String dateRule,
        @NotNull @Min(1) Integer serialLength,
        @NotBlank String resetRule,
        @NotBlank String sampleNo,
        String status,
        String remark
) {
}
