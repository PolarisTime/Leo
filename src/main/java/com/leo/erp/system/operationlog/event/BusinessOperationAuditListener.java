package com.leo.erp.system.operationlog.event;

import com.leo.erp.system.operationlog.service.OperationLogCommand;
import com.leo.erp.system.operationlog.service.OperationLogService;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class BusinessOperationAuditListener {

    private final OperationLogService operationLogService;

    public BusinessOperationAuditListener(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @ApplicationModuleListener(id = "business-operation-audit-v1")
    public void record(BusinessOperationEvent event) {
        operationLogService.record(new OperationLogCommand(
                event.moduleName(),
                event.actionType(),
                event.businessNo(),
                "EVENT",
                resolveEventPath(event.eventType()),
                null,
                "成功",
                event.remark(),
                event.aggregateId(),
                event.moduleKey(),
                event.operatorId(),
                event.operatorName(),
                event.loginName(),
                event.authType(),
                event.eventId(),
                event.traceId(),
                event.aggregateType(),
                event.eventVersion()
        ));
    }

    private String resolveEventPath(String eventType) {
        return "/domain-events/" + eventType.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
