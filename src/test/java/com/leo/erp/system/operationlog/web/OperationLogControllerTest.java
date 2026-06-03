package com.leo.erp.system.operationlog.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.system.operationlog.service.OperationLogService;
import com.leo.erp.system.operationlog.web.dto.OperationLogResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationLogControllerTest {

    private final OperationLogService operationLogService = mock(OperationLogService.class);
    private final OperationLogController controller = new OperationLogController(operationLogService);

    @Test
    void pageReturnsPaginatedLogs() {
        OperationLogResponse item = mock(OperationLogResponse.class);
        Page<OperationLogResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        LocalDate startTime = LocalDate.of(2024, 1, 1);
        LocalDate endTime = LocalDate.of(2024, 12, 31);
        when(operationLogService.page(any(), any())).thenReturn(page);

        ApiResponse<PageResponse<OperationLogResponse>> response = controller.page(
                query, "test", "用户账户", "新增", "success", startTime, endTime, 1L, "login");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }
}
