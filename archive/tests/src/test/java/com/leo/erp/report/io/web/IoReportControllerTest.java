package com.leo.erp.report.io.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.report.io.service.IoReportService;
import com.leo.erp.report.io.web.dto.IoReportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IoReportControllerTest {

    private final IoReportService ioReportService = mock(IoReportService.class);
    private final IoReportController controller = new IoReportController(ioReportService);

    @Test
    void pageReturnsPaginatedIoReports() {
        IoReportResponse item = mock(IoReportResponse.class);
        Page<IoReportResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 12, 31);
        when(ioReportService.page(any(), eq("test"), eq("inbound"), eq(startDate), eq(endDate))).thenReturn(page);

        ApiResponse<PageResponse<IoReportResponse>> response = controller.page(query, "test", "inbound", startDate, endDate);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void pageWithNullOptionalParamsDelegatesToService() {
        Page<IoReportResponse> page = new PageImpl<>(List.of());
        PageQuery query = new PageQuery(0, 20, null, null);
        when(ioReportService.page(any(), isNull(), isNull(), isNull(), isNull())).thenReturn(page);

        ApiResponse<PageResponse<IoReportResponse>> response = controller.page(query, null, null, null, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).isEmpty();
        verify(ioReportService).page(query, null, null, null, null);
    }
}
