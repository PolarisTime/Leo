package com.leo.erp.system.printtemplate.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PrintTemplateRequest(
        @NotBlank @Size(max = 64) String billType,
        @NotBlank @Size(max = 128) String templateName,
        @NotBlank @Size(max = 200000) String templateHtml,
        @Pattern(regexp = "[01]", message = "默认模板标记不合法")
        String isDefault
) {
}
