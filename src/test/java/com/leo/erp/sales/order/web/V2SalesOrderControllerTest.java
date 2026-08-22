package com.leo.erp.sales.order.web;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.sales.order.service.SalesOrderPrintExportService;
import com.leo.erp.sales.order.service.SalesOrderService;
import com.leo.erp.sales.order.service.SalesOrderSourceCandidateService;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.sales.order.web.dto.SalesOrderSourceCandidateResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V2SalesOrderController 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class V2SalesOrderControllerTest {

    @Mock
    private SalesOrderService service;

    @Mock
    private SalesOrderPrintExportService printExportService;

    @Mock
    private SalesOrderSourceCandidateService sourceCandidateService;

    @InjectMocks
    private V2SalesOrderController controller;

    @BeforeEach
    void setUpServletContext() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void clearServletContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private SalesOrderRequest request() {
        return new SalesOrderRequest(
                "SO001", null, null, "CUST001", 10L, "客户A", 20L, "项目A", null, null,
                LocalDate.of(2026, 8, 1), "销售员A", "DRAFT", null, List.of(), List.of(), false);
    }

    @Test
    void search_shouldCapLimit() {
        when(service.search("", 500)).thenReturn(List.of(mock(SalesOrderResponse.class)));

        var result = controller.search(null, 1000);

        assertThat(result).hasSize(1);
        verify(service).search("", 500);
    }

    @Test
    void page_shouldDelegate() {
        when(service.page(any(PageQuery.class), any(PageFilter.class), anyString()))
                .thenReturn(mock(org.springframework.data.domain.Page.class));

        var result = controller.page(mock(PageQuery.class), "kw", 10L, "客户A", 20L, "项目A", 30L,
                "M001", "DRAFT", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(result).isNotNull();
    }

    @Test
    void outboundImportCandidates_shouldDelegate() {
        when(service.outboundImportCandidates(any(PageQuery.class), any(PageFilter.class)))
                .thenReturn(mock(org.springframework.data.domain.Page.class));

        var result = controller.outboundImportCandidates(mock(PageQuery.class), "kw", 10L, "客户A", 20L,
                "项目A", 30L, "DRAFT", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 5L);

        assertThat(result).isNotNull();
    }

    @Test
    void sourceCandidates_shouldDelegate() {
        @SuppressWarnings("unchecked")
        PageResponse<SalesOrderSourceCandidateResponse> expected =
                (PageResponse<SalesOrderSourceCandidateResponse>) mock(PageResponse.class);
        when(sourceCandidateService.page(anyString(), anyLong(), anyLong(), any(), any(), anyLong(), any(PageQuery.class)))
                .thenReturn(expected);

        var result = controller.sourceCandidates(mock(PageQuery.class), "kw", 1L, 2L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 5L);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void detail_shouldDelegate() {
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(service.detail(5L)).thenReturn(response);

        assertThat(controller.detail(5L)).isSameAs(response);
    }

    @Test
    void createXlsxExport_shouldReturnFile() {
        FileDownloadResponse download = mock(FileDownloadResponse.class);
        when(download.contentType()).thenReturn(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
        when(download.filename()).thenReturn("SO001.xlsx");
        when(download.content()).thenReturn(new byte[]{1, 2, 3});
        when(printExportService.exportSalesOrderPrint(anyLong(), any())).thenReturn(download);

        ResponseEntity<byte[]> result = controller.createXlsxExport(5L, null, new MockHttpServletRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).containsExactly(1, 2, 3);
    }

    @Test
    void create_shouldReturnCreated() {
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(service.create(any())).thenReturn(response);

        ResponseEntity<SalesOrderResponse> result = controller.create(request());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void update_shouldDelegate() {
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(service.update(anyLong(), any())).thenReturn(response);

        assertThat(controller.update(5L, request())).isSameAs(response);
    }

    @Test
    void updateAndComplete_shouldDelegate() {
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(service.updateAndComplete(anyLong(), any())).thenReturn(response);

        assertThat(controller.updateAndComplete(5L, request())).isSameAs(response);
    }

    @Test
    void updateStatus_shouldPassStatus() {
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(service.updateStatus(anyLong(), anyString())).thenReturn(response);

        assertThat(controller.updateStatus(5L, new StatusUpdateRequest("AUDITED"))).isSameAs(response);
        verify(service).updateStatus(5L, "AUDITED");
    }

    @Test
    void complete_shouldDelegate() {
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(service.completeSalesOrder(anyLong())).thenReturn(response);

        assertThat(controller.complete(5L)).isSameAs(response);
    }

    @Test
    void delete_shouldReturnNoContent() {
        org.mockito.Mockito.doNothing().when(service).delete(anyLong());

        ResponseEntity<Void> result = controller.delete(5L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
