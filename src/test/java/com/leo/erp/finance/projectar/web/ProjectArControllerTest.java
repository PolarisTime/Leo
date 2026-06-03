package com.leo.erp.finance.projectar.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.finance.projectar.service.ProjectArService;
import com.leo.erp.finance.projectar.web.dto.ProjectArDetailRowResponse;
import com.leo.erp.finance.projectar.web.dto.ProjectArSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectArControllerTest {

    private final ProjectArService projectArService = mock(ProjectArService.class);
    private final ProjectArController controller = new ProjectArController(projectArService);

    @Test
    void pageSummaryReturnsPaginatedSummaries() {
        ProjectArSummaryResponse item = mock(ProjectArSummaryResponse.class);
        Page<ProjectArSummaryResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(projectArService.pageSummary(any(), eq("test"), eq(1L))).thenReturn(page);

        ApiResponse<PageResponse<ProjectArSummaryResponse>> response = controller.pageSummary(query, "test", 1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void pageUnreconciledReturnsPaginatedDetails() {
        ProjectArDetailRowResponse item = mock(ProjectArDetailRowResponse.class);
        Page<ProjectArDetailRowResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(projectArService.pageUnreconciled(eq(1L), any())).thenReturn(page);

        ApiResponse<PageResponse<ProjectArDetailRowResponse>> response = controller.pageUnreconciled(1L, query);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void pageReconciledReturnsPaginatedDetails() {
        ProjectArDetailRowResponse item = mock(ProjectArDetailRowResponse.class);
        Page<ProjectArDetailRowResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(projectArService.pageReconciled(eq(1L), any())).thenReturn(page);

        ApiResponse<PageResponse<ProjectArDetailRowResponse>> response = controller.pageReconciled(1L, query);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }
}
