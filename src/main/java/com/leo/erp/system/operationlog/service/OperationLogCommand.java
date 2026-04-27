package com.leo.erp.system.operationlog.service;

public record OperationLogCommand(
        String moduleName,
        String actionType,
        String businessNo,
        String requestMethod,
        String requestPath,
        String clientIp,
        String resultStatus,
        String remark,
        Long operatorId,
        String operatorName,
        String loginName
) {
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
        this(moduleName, actionType, businessNo, requestMethod, requestPath, clientIp, resultStatus, remark, null, null, null);
    }
}
