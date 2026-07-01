package com.leo.erp.attachment.web.dto;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public record AttachmentDirectUploadPrepareResponse(
        Long attachmentId,
        String token,
        String objectKey,
        String storagePath,
        URI uploadUrl,
        String method,
        Map<String, String> headers,
        Instant expiresAt
) {
}
