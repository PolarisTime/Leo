package com.leo.erp.system.operationlog.web.dto;

import java.time.LocalDateTime;

public record OperationLogResponse(
        Long id,
        String logNo,
        String operatorName,
        String loginName,
        String moduleName,
        String actionType,
        String businessNo,
        String requestMethod,
        String requestPath,
        String clientIp,
        String resultStatus,
        LocalDateTime operationTime,
        String remark
) {
}
