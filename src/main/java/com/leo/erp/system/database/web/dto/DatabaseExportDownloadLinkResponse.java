package com.leo.erp.system.database.web.dto;

import java.time.LocalDateTime;

public record DatabaseExportDownloadLinkResponse(
        String downloadUrl,
        LocalDateTime expiresAt
) {
}
