package com.leo.erp.system.operationlog.web.dto;

import java.time.LocalDateTime;

public record OperationLogResponse(
        Long id,
        String logNo,
        String operatorName,
        String loginName,
        String authType,
        String moduleName,
        String actionType,
        String businessNo,
        Long recordId,
        String moduleKey,
        String requestMethod,
        String requestPath,
        String clientIp,
        String resultStatus,
        LocalDateTime operationTime,
        String remark
) {
}
