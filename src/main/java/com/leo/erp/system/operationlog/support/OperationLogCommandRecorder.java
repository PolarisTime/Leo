package com.leo.erp.system.operationlog.support;

import com.leo.erp.system.operationlog.service.OperationLogCommand;
import com.leo.erp.system.operationlog.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 拦截器采集入口: 把请求上下文补全为 {@link OperationLogCommand} 并交给服务层落库。
 *
 * <p>采集顺序: 先按 {@link OperationLogRequestPolicy} 判断是否跳过(去噪/去重), 再组装字段。
 * 所有采集动作都在 try/catch 内, 日志写入失败只 warn, 绝不影响主请求。</p>
 */
@Slf4j
@Component
public class OperationLogCommandRecorder {
    private final OperationLogService operationLogService;
    private final OperationLogResultCollector resultCollector;
    private final OperationLogRequestBodySanitizer requestBodySanitizer;
    private final OperationLogRequestPolicy requestPolicy;

    public OperationLogCommandRecorder(OperationLogService operationLogService,
                                       OperationLogResultCollector resultCollector,
                                       OperationLogRequestBodySanitizer requestBodySanitizer,
                                       OperationLogRequestPolicy requestPolicy) {
        this.operationLogService = operationLogService;
        this.resultCollector = resultCollector;
        this.requestBodySanitizer = requestBodySanitizer;
        this.requestPolicy = requestPolicy;
    }

    public void record(HttpServletRequest request,
                       OperationLogMetadata metadata,
                       Exception ex,
                       int responseStatus) {
        try {
            if (requestPolicy.shouldSkip(request)) {
                // /auth/login 由 LoginService 显式记录「身份认证/登录」; /auth/refresh 默认视为会话噪声
                return;
            }
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
                    null,
                    null,
                    null,
                    resultCollector.resolveTraceId(),
                    null,
                    null,
                    responseStatus,
                    resultCollector.resolveDurationMs(request),
                    resultCollector.resolveUserAgent(request),
                    resultCollector.resolveErrorCode(request, ex),
                    resultCollector.resolveErrorMessage(request, ex),
                    requestBodySanitizer.sanitize(request),
                    requestPolicy.resolveCategory(request)
            ));
        } catch (Exception logEx) {
            log.warn("操作日志写入失败: {} {}", request.getMethod(), request.getRequestURI(), logEx);
        }
    }
}
