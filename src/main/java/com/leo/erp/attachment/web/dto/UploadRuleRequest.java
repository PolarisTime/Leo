package com.leo.erp.attachment.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UploadRuleRequest(
        @NotBlank @Size(max = 255) String renamePattern,
        @NotBlank @Size(max = 16) String status,
        @Size(max = 255) String remark
) {
}
