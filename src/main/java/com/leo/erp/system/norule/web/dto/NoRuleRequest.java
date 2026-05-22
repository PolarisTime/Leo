package com.leo.erp.system.norule.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NoRuleRequest(
        @NotBlank @Size(max = 64) String settingCode,
        @NotBlank @Size(max = 128) String settingName,
        @NotBlank @Size(max = 128) String billName,
        @NotBlank @Size(max = 64) String prefix,
        @NotBlank @Size(max = 32) String dateRule,
        @NotNull @Min(1) Integer serialLength,
        @NotBlank @Size(max = 32) String resetRule,
        @NotBlank @Size(max = 64) String sampleNo,
        @Size(max = 16) String status,
        @Size(max = 255) String remark
) {
}
