package com.leo.erp.system.printtemplate.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PrintTemplateRequest(
        @NotBlank @Size(max = 64) String billType,
        @NotBlank @Size(max = 128) String templateName,
        @Size(max = 96) String templateCode,
        @Size(max = 200000) String templateHtml,
        @Pattern(regexp = "COORD|PDF_FORM", message = "模板类型仅支持 COORD 或 PDF_FORM")
        String templateType,
        @Pattern(regexp = "LODOP|PDF_FORM", message = "渲染引擎仅支持 LODOP 或 PDF_FORM")
        String engine,
        @Size(max = 255) String assetRef,
        Long settlementCompanyId,
        @Size(max = 128) String settlementCompanyName,
        Integer versionNo,
        @Pattern(regexp = "ACTIVE|DISABLED", message = "模板状态仅支持 ACTIVE 或 DISABLED")
        String status
) {
    public PrintTemplateRequest(
            String billType,
            String templateName,
            String templateCode,
            String templateHtml,
            String templateType,
            String engine,
            String assetRef,
            Integer versionNo,
            String status
    ) {
        this(
                billType,
                templateName,
                templateCode,
                templateHtml,
                templateType,
                engine,
                assetRef,
                null,
                null,
                versionNo,
                status
        );
    }
}
