package com.leo.erp.attachment.web.dto;

public record AttachmentAccessUrlResponse(
        String url,
        boolean inline,
        boolean presigned
) {
}
