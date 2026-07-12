package com.leo.erp.report.inventory.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.report.inventory.service.InventoryReportService;
import com.leo.erp.report.inventory.web.dto.InventoryReportExportRequest;
import com.leo.erp.report.inventory.web.dto.InventoryReportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReportControllerTest {

    private final InventoryReportService inventoryReportService = mock(InventoryReportService.class);
    private final InventoryReportController controller = new InventoryReportController(inventoryReportService);

    @Test
    void pageReturnsPaginatedInventoryReports() {
        InventoryReportResponse item = mock(InventoryReportResponse.class);
        Page<InventoryReportResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(inventoryReportService.page(any(), eq("test"), eq(101L), eq("category"), eq(true)))
                .thenReturn(page);

        ApiResponse<PageResponse<InventoryReportResponse>> response =
                controller.page(query, "test", 101L, "category", true);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void exportReturnsFileDownload() {
        byte[] content = "test".getBytes();
        FileDownloadResponse file = new FileDownloadResponse("test.xlsx", MediaType.APPLICATION_OCTET_STREAM, content);
        when(inventoryReportService.exportExcel(eq("test"), eq(101L), eq("category"), eq(true)))
                .thenReturn(file);

        InventoryReportExportRequest request = new InventoryReportExportRequest("test", 101L, "category", true);
        ResponseEntity<byte[]> response = controller.export(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(content);
        verify(inventoryReportService).exportExcel("test", 101L, "category", true);
    }

    @Test
    void exportWithNullRequestReturnsFileDownload() {
        byte[] content = "test".getBytes();
        FileDownloadResponse file = new FileDownloadResponse("test.xlsx", MediaType.APPLICATION_OCTET_STREAM, content);
        when(inventoryReportService.exportExcel(eq(null), eq(null), eq(null), eq(null))).thenReturn(file);

        ResponseEntity<byte[]> response = controller.export(null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(content);
    }

    @Test
    void pageWithNullOptionalParamsDelegatesToService() {
        Page<InventoryReportResponse> page = new PageImpl<>(List.of());
        PageQuery query = new PageQuery(0, 20, null, null);
        when(inventoryReportService.page(any(), eq(null), eq(null), eq(null), eq(null))).thenReturn(page);

        ApiResponse<PageResponse<InventoryReportResponse>> response = controller.page(query, null, null, null, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).isEmpty();
        verify(inventoryReportService).page(query, null, null, null, null);
    }
}
