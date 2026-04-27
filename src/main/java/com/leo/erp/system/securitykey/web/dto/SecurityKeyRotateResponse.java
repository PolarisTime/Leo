package com.leo.erp.system.securitykey.web.dto;

import java.time.LocalDateTime;

public record SecurityKeyRotateResponse(
        String keyCode,
        String source,
        Integer activeVersion,
        String activeFingerprint,
        LocalDateTime rotatedAt,
        int processedRecordCount,
        int retiredVersionCount,
        String remark
) {
}
