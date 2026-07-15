package com.leo.erp.system.operationlog.service;

import java.util.UUID;

public record OperationLogCommand(
        String moduleName,
        String actionType,
        String businessNo,
        String requestMethod,
        String requestPath,
        String clientIp,
        String resultStatus,
        String remark,
        Long recordId,
        String moduleKey,
        Long operatorId,
        String operatorName,
        String loginName,
        String authType,
        UUID eventId,
        String traceId,
        String aggregateType,
        Integer eventVersion
) {
    public OperationLogCommand(
            String moduleName,
            String actionType,
            String businessNo,
            String requestMethod,
            String requestPath,
            String clientIp,
            String resultStatus,
            String remark,
            Long recordId,
            String moduleKey,
            Long operatorId,
            String operatorName,
            String loginName
    ) {
        this(moduleName, actionType, businessNo, requestMethod, requestPath, clientIp, resultStatus, remark,
                recordId, moduleKey, operatorId, operatorName, loginName, null, null, null, null, null);
    }

    public OperationLogCommand(
            String moduleName,
            String actionType,
            String businessNo,
            String requestMethod,
            String requestPath,
            String clientIp,
            String resultStatus,
            String remark
    ) {
        this(moduleName, actionType, businessNo, requestMethod, requestPath, clientIp, resultStatus, remark,
                null, null, null, null, null, null, null, null, null, null);
    }
}
