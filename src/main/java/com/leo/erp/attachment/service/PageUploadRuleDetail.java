package com.leo.erp.attachment.service;

public record PageUploadRuleDetail(
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
