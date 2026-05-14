package com.leo.erp.system.database.service;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public record DatabaseExportDownloadResource(
        Resource resource,
        MediaType contentType,
        long contentLength,
        String contentDisposition
) {
}
