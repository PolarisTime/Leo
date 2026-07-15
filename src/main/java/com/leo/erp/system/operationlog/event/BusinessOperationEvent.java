package com.leo.erp.system.operationlog.event;

import java.time.Instant;
import java.util.UUID;

public record BusinessOperationEvent(
        UUID eventId,
        int eventVersion,
        Instant occurredAt,
        String eventType,
        String moduleKey,
        String moduleName,
        String actionType,
        String aggregateType,
        Long aggregateId,
        String businessNo,
        Long operatorId,
        String operatorName,
        String loginName,
        String authType,
        String traceId,
        String remark
) {
}
