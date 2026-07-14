package com.leo.erp.report.pendinginvoicereceipt.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.report.pendinginvoicereceipt.service.PendingInvoiceReceiptReportService;
import com.leo.erp.report.pendinginvoicereceipt.web.dto.PendingInvoiceReceiptReportResponse;
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

class PendingInvoiceReceiptReportControllerTest {

    private final PendingInvoiceReceiptReportService pendingInvoiceReceiptReportService = mock(PendingInvoiceReceiptReportService.class);
    private final PendingInvoiceReceiptReportController controller = new PendingInvoiceReceiptReportController(pendingInvoiceReceiptReportService);

    @Test
    void pageReturnsPaginatedReports() {
        PendingInvoiceReceiptReportResponse item = mock(PendingInvoiceReceiptReportResponse.class);
        Page<PendingInvoiceReceiptReportResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 12, 31);
        when(pendingInvoiceReceiptReportService.page(any(), eq("test"), eq("supplier"), eq(startDate), eq(endDate))).thenReturn(page);

        ApiResponse<PageResponse<PendingInvoiceReceiptReportResponse>> response = controller.page(query, "test", "supplier", startDate, endDate);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void pageWithNullOptionalParamsDelegatesToService() {
        Page<PendingInvoiceReceiptReportResponse> page = new PageImpl<>(List.of());
        PageQuery query = new PageQuery(0, 20, null, null);
        when(pendingInvoiceReceiptReportService.page(any(), isNull(), isNull(), isNull(), isNull())).thenReturn(page);

        ApiResponse<PageResponse<PendingInvoiceReceiptReportResponse>> response = controller.page(query, null, null, null, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).isEmpty();
        verify(pendingInvoiceReceiptReportService).page(query, null, null, null, null);
    }
}
