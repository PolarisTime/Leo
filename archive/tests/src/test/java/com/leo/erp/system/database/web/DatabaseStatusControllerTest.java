package com.leo.erp.system.database.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.system.database.service.DatabaseStatusService;
import com.leo.erp.system.database.web.dto.DatabaseMonitoringResponse;
import com.leo.erp.system.database.web.dto.DatabaseStatusResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseStatusControllerTest {

    private final DatabaseStatusService statusService = mock(DatabaseStatusService.class);
    private final DatabaseStatusController controller = new DatabaseStatusController(statusService);

    @Test
    void statusReturnsDatabaseStatus() {
        DatabaseStatusResponse status = mock(DatabaseStatusResponse.class);
        when(statusService.getStatus()).thenReturn(status);

        ApiResponse<DatabaseStatusResponse> response = controller.status();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(status);
    }

    @Test
    void monitoringReturnsDatabaseMonitoring() {
        DatabaseMonitoringResponse monitoring = mock(DatabaseMonitoringResponse.class);
        when(statusService.getMonitoring()).thenReturn(monitoring);

        ApiResponse<DatabaseMonitoringResponse> response = controller.monitoring();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(monitoring);
    }
}
