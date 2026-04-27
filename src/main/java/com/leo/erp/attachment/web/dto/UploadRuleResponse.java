package com.leo.erp.attachment.web.dto;

public record UploadRuleResponse(
        Long id,
        String moduleKey,
        String moduleName,
        String ruleCode,
        String ruleName,
        String renamePattern,
        String status,
        String remark,
        String previewFileName
) {
}
