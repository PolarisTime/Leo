package com.leo.erp.system.operationlog.web.dto;

import java.time.LocalDateTime;
import java.util.UUID;

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
        UUID eventId,
        String traceId,
        String aggregateType,
        Integer eventVersion,
        String remark
) {
}
