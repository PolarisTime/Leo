package com.leo.erp.system.operationlog.support;

import com.leo.erp.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class OperationLogInterceptor implements HandlerInterceptor {

    static final String METADATA_ATTRIBUTE = OperationLogInterceptor.class.getName() + ".metadata";

    private final OperationLogMetadataResolver metadataResolver;
    private final OperationLogResultCollector resultCollector;
    private final OperationLogCommandRecorder commandRecorder;

    public OperationLogInterceptor(OperationLogMetadataResolver metadataResolver,
                                   OperationLogResultCollector resultCollector,
                                   OperationLogCommandRecorder commandRecorder) {
        this.metadataResolver = metadataResolver;
        this.resultCollector = resultCollector;
        this.commandRecorder = commandRecorder;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        OperationLogMetadata metadata = metadataResolver.resolveMetadata(handlerMethod, request);
        if (metadata == null) {
            return true;
        }

        request.setAttribute(METADATA_ATTRIBUTE, metadata);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        OperationLogMetadata metadata = (OperationLogMetadata) request.getAttribute(METADATA_ATTRIBUTE);
        if (metadata == null) {
            return;
        }

        ApiResponse<?> apiResponse = resultCollector.extractApiResponse(request);
        commandRecorder.record(request, metadata, apiResponse, ex, response.getStatus());
    }
}
