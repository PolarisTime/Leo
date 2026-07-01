package com.leo.erp.attachment.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AttachmentDirectUploadCompleteRequest(
        @NotNull(message = "附件 ID 不能为空") @Positive(message = "附件 ID 必须大于 0") Long attachmentId,
        @NotBlank(message = "直传凭证不能为空") String token
) {
}
