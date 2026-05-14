package com.leo.erp.common.web.dto;

import org.springframework.http.MediaType;

public record FileDownloadResponse(
        String filename,
        MediaType contentType,
        byte[] content
) {
}
