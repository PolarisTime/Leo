package com.leo.erp.system.printtemplate.web.dto;

import java.time.LocalDateTime;

public record PrintTemplateResponse(
        Long id,
        String templateName,
        String templateCode,
        String templateHtml,
        String billType,
        String templateType,
        String engine,
        String assetRef,
        Integer versionNo,
        String status,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
