package com.leo.erp.system.securitykey.web.dto;

import java.time.LocalDateTime;

public record SecurityKeyItemResponse(
        String keyCode,
        String keyName,
        String source,
        Integer activeVersion,
        String activeFingerprint,
        LocalDateTime activatedAt,
        int retiredVersionCount,
        int protectedRecordCount,
        String remark
) {
}
