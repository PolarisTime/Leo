package com.leo.erp.attachment.web.dto;

import jakarta.validation.constraints.NotBlank;

public record AttachmentUploadCompletionRequest(
        @NotBlank(message = "直传凭证不能为空") String token
) {
}
