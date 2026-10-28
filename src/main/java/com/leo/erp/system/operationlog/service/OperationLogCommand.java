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
        Integer eventVersion,
        Integer statusCode,
        Integer durationMs,
        String userAgent,
        String errorCode,
        String errorMessage,
        String requestBody,
        String operationCategory
) {
    /** 兼容 V198 之前的 18 参数构造(领域事件链路): 采集增强字段全部为空。 */
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
            String loginName,
            String authType,
            UUID eventId,
            String traceId,
            String aggregateType,
            Integer eventVersion
    ) {
        this(moduleName, actionType, businessNo, requestMethod, requestPath, clientIp, resultStatus, remark,
                recordId, moduleKey, operatorId, operatorName, loginName, authType, eventId, traceId,
                aggregateType, eventVersion, null, null, null, null, null, null, null);
    }

    /** 服务层显式记录(登录/退出等)使用的构造: 只关心业务字段。 */
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
                recordId, moduleKey, operatorId, operatorName, loginName, null, null, null, null, null,
                null, null, null, null, null, null, null);
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
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }
}
