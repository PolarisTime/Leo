package com.leo.erp.common.setting;

public record PageUploadRuleSummary(
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
