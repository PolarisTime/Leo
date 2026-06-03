package com.leo.erp.system.dashboard.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import com.leo.erp.system.dashboard.web.dto.DashboardSummaryResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardControllerTest {

    private final DashboardSummaryService dashboardSummaryService = mock(DashboardSummaryService.class);
    private final DashboardController controller = new DashboardController(dashboardSummaryService);

    @Test
    void summaryReturnsDashboardSummary() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        when(principal.id()).thenReturn(1L);
        DashboardSummaryResponse summary = mock(DashboardSummaryResponse.class);
        when(dashboardSummaryService.getSummary(1L)).thenReturn(summary);

        ApiResponse<DashboardSummaryResponse> response = controller.summary(principal);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(summary);
        verify(dashboardSummaryService).getSummary(1L);
    }
}