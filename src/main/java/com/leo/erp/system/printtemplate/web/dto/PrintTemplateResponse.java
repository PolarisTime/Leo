package com.leo.erp.system.printtemplate.web.dto;

import java.time.LocalDateTime;

public record PrintTemplateResponse(
        String id,
        String templateName,
        String templateCode,
        String templateHtml,
        String billType,
        String templateType,
        String engine,
        String assetRef,
        Integer versionNo,
        String status,
        String syncMode,
        String sourceRef,
        String sourceChecksum,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
