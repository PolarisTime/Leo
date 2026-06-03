package com.leo.erp.system.operationlog.support;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.system.operationlog.service.OperationLogCommand;
import com.leo.erp.system.operationlog.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class OperationLogCommandRecorderTest {

    @Test
    void shouldRecordLog_whenNoException() {
        var operationLogService = mock(OperationLogService.class);
        var resultCollector = mock(OperationLogResultCollector.class);
        var recorder = new OperationLogCommandRecorder(operationLogService, resultCollector);

        var request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/test");

        var metadata = new OperationLogMetadata("测试模块", "新增", new String[]{"id"}, false);
        var apiResponse = ApiResponse.success("data");

        when(resultCollector.resolveResultStatus(any(), any(), any())).thenReturn("成功");
        when(resultCollector.resolveBusinessNo(any(), any(), any())).thenReturn("BIZ001");
        when(resultCollector.resolveRemark(any(), any())).thenReturn("操作成功");
        when(resultCollector.resolveRequestPath(any())).thenReturn("/api/test");
        when(resultCollector.resolveIp(any())).thenReturn("127.0.0.1");

        recorder.record(request, metadata, apiResponse, null, 200);

        verify(operationLogService).record(any(OperationLogCommand.class));
    }

    @Test
    void shouldNotThrowException_whenLogWriteFails() {
        var operationLogService = mock(OperationLogService.class);
        var resultCollector = mock(OperationLogResultCollector.class);
        var recorder = new OperationLogCommandRecorder(operationLogService, resultCollector);

        var request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/test");

        var metadata = new OperationLogMetadata("测试模块", "新增", new String[]{"id"}, false);

        when(resultCollector.resolveResultStatus(any(), any(), any())).thenThrow(new RuntimeException("test error"));

        recorder.record(request, metadata, null, null, 500);

        verify(operationLogService, never()).record(any());
    }
}
