package com.leo.erp.common.web.dto;

import org.springframework.http.MediaType;

public record FileDownloadResponse(
        String filename,
        MediaType contentType,
        byte[] content,
        String businessNo,
        Long recordId,
        String moduleKey
) {
    public FileDownloadResponse(String filename, MediaType contentType, byte[] content) {
        this(filename, contentType, content, null, null, null);
    }
}
