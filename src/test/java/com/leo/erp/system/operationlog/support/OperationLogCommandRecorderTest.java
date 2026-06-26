package com.leo.erp.system.operationlog.support;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.system.operationlog.service.OperationLogCommand;
import com.leo.erp.system.operationlog.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

        var metadata = new OperationLogMetadata("测试模块", "", "新增", new String[]{"id"}, "id", "moduleKey", false);
        var apiResponse = ApiResponse.success("data");

        when(resultCollector.resolveResultStatus(any(), anyInt(), any())).thenReturn("成功");
        when(resultCollector.resolveModuleName(any(), any())).thenReturn("测试模块");
        when(resultCollector.resolveBusinessNo(any(), any(), any())).thenReturn("BIZ001");
        when(resultCollector.resolveRecordId(any(), any(), any())).thenReturn(1L);
        when(resultCollector.resolveModuleKey(any(), any(), any())).thenReturn("sales-order");
        when(resultCollector.resolveRemark(any(), any())).thenReturn("操作成功");
        when(resultCollector.resolveRequestPath(any())).thenReturn("/api/test");
        when(resultCollector.resolveIp(any())).thenReturn("127.0.0.1");

        recorder.record(request, metadata, apiResponse, null, 200);

        verify(operationLogService).record(argThat(command -> {
            assertThat(command.businessNo()).isEqualTo("BIZ001");
            assertThat(command.recordId()).isEqualTo(1L);
            assertThat(command.moduleKey()).isEqualTo("sales-order");
            return true;
        }));
    }

    @Test
    void shouldNotThrowException_whenLogWriteFails() {
        var operationLogService = mock(OperationLogService.class);
        var resultCollector = mock(OperationLogResultCollector.class);
        var recorder = new OperationLogCommandRecorder(operationLogService, resultCollector);

        var request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/test");

        var metadata = new OperationLogMetadata("测试模块", "", "新增", new String[]{"id"}, "", "", false);

        when(resultCollector.resolveResultStatus(any(), anyInt(), any())).thenThrow(new RuntimeException("test error"));

        recorder.record(request, metadata, null, null, 500);

        verify(operationLogService, never()).record(any());
    }
}
