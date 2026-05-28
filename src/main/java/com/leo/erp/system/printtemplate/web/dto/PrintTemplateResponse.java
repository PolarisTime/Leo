package com.leo.erp.system.printtemplate.web.dto;

import java.time.LocalDateTime;

public record PrintTemplateResponse(
        Long id,
        String templateName,
        String templateHtml,
        String billType,
        String templateType,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
