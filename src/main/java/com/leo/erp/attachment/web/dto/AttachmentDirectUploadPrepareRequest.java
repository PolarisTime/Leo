package com.leo.erp.attachment.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AttachmentDirectUploadPrepareRequest(
        @NotBlank(message = "文件名不能为空") @Size(max = 255) String fileName,
        @Size(max = 255) String contentType,
        @NotNull(message = "文件大小不能为空") @Positive(message = "文件大小必须大于 0") Long fileSize,
        @Size(max = 32) String sourceType,
        @NotBlank(message = "文件校验值不能为空") @Size(min = 64, max = 64) String sha256Hex
) {
}
