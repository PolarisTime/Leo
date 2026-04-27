package com.leo.erp.attachment.service;

public record UpdatePageUploadRuleCommand(
        String renamePattern,
        String status,
        String remark
) {
}
