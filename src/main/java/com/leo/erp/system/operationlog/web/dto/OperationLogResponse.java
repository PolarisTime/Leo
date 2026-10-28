package com.leo.erp.system.operationlog.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 操作日志响应。
 *
 * <p>{@code statusCode}/{@code durationMs} 为数字; {@code requestBody} 以 JSON 对象返回,
 * 历史数据为 null。雪花 ID 由全局 Jackson 配置序列化为字符串。</p>
 */
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
        String remark,
        Integer statusCode,
        Integer durationMs,
        String userAgent,
        String errorCode,
        String errorMessage,
        JsonNode requestBody,
        String operationCategory
) {
}
