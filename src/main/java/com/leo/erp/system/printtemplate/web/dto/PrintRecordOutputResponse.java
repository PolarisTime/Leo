package com.leo.erp.system.printtemplate.web.dto;

import java.util.List;
import java.util.Map;

/**
 * 打印输出响应：service 层 PrintOutput 的 web 契约形态，
 * 字段结构与 JSON 输出保持一致（data/items 为字符串映射），不泄漏 service 类型。
 */
public record PrintRecordOutputResponse(
        String kind,
        String templateName,
        String templateType,
        String contentType,
        String fileName,
        String pdfBase64,
        String businessNo,
        Long recordId,
        String moduleKey,
        String templateHtml,
        Map<String, String> data,
        List<Map<String, String>> items
) {

    public static PrintRecordOutputResponse from(
            com.leo.erp.system.printtemplate.service.PrintOutput output) {
        return new PrintRecordOutputResponse(
                output.kind() == null ? null : output.kind().name(),
                output.templateName(),
                output.templateType(),
                output.contentType(),
                output.fileName(),
                output.pdfBase64(),
                output.businessNo(),
                output.recordId(),
                output.moduleKey(),
                output.templateHtml(),
                output.data(),
                output.items()
        );
    }
}
