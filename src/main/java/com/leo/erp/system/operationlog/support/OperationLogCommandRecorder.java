package com.leo.erp.system.operationlog.support;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.system.operationlog.service.OperationLogCommand;
import com.leo.erp.system.operationlog.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OperationLogCommandRecorder {

    private static final Logger log = LoggerFactory.getLogger(OperationLogCommandRecorder.class);

    private final OperationLogService operationLogService;
    private final OperationLogResultCollector resultCollector;

    public OperationLogCommandRecorder(OperationLogService operationLogService,
                                       OperationLogResultCollector resultCollector) {
        this.operationLogService = operationLogService;
        this.resultCollector = resultCollector;
    }

    public void record(HttpServletRequest request, OperationLogMetadata metadata,
                       ApiResponse<?> apiResponse, Exception ex, int responseStatus) {
        try {
            String resultStatus = resultCollector.resolveResultStatus(apiResponse, null, ex);
            String businessNo = resultCollector.resolveBusinessNo(request, apiResponse, metadata);
            String remark = resultCollector.resolveRemark(apiResponse, ex);
            operationLogService.record(new OperationLogCommand(
                    metadata.moduleName(),
                    metadata.actionType(),
                    businessNo,
                    request.getMethod(),
                    resultCollector.resolveRequestPath(request),
                    resultCollector.resolveIp(request),
                    resultStatus,
                    remark
            ));
        } catch (Exception logEx) {
            log.warn("操作日志写入失败: {} {}", request.getMethod(), request.getRequestURI(), logEx);
        }
    }
}
