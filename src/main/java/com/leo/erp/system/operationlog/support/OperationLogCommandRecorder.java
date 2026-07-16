package com.leo.erp.system.operationlog.support;

import lombok.extern.slf4j.Slf4j;
import com.leo.erp.system.operationlog.service.OperationLogCommand;
import com.leo.erp.system.operationlog.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OperationLogCommandRecorder {
    private final OperationLogService operationLogService;
    private final OperationLogResultCollector resultCollector;

    public OperationLogCommandRecorder(OperationLogService operationLogService,
                                       OperationLogResultCollector resultCollector) {
        this.operationLogService = operationLogService;
        this.resultCollector = resultCollector;
    }

    public void record(HttpServletRequest request,
                       OperationLogMetadata metadata,
                       Exception ex,
                       int responseStatus) {
        try {
            String resultStatus = resultCollector.resolveResultStatus(responseStatus, ex);
            String moduleName = resultCollector.resolveModuleName(request, metadata);
            String businessNo = resultCollector.resolveBusinessNo(request, metadata);
            Long recordId = resultCollector.resolveRecordId(request, metadata);
            String moduleKey = resultCollector.resolveModuleKey(request, metadata);
            String remark = resultCollector.resolveRemark(ex);
            operationLogService.record(new OperationLogCommand(
                    moduleName,
                    metadata.actionType(),
                    businessNo,
                    request.getMethod(),
                    resultCollector.resolveRequestPath(request),
                    resultCollector.resolveIp(request),
                    resultStatus,
                    remark,
                    recordId,
                    moduleKey,
                    null,
                    null,
                    null
            ));
        } catch (Exception logEx) {
            log.warn("操作日志写入失败: {} {}", request.getMethod(), request.getRequestURI(), logEx);
        }
    }
}
