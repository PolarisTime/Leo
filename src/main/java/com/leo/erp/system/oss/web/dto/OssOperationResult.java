package com.leo.erp.system.oss.web.dto;

import java.util.List;

public record OssOperationResult(
        boolean success,
        String stage,
        String message,
        String objectKey,
        List<String> details
) {
}
