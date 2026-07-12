package com.leo.erp.sales.order.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.sales.order.service.SalesOrderPrintExportService;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.sales.order.service.SalesOrderPrintXlsxOptions;
import com.leo.erp.sales.order.service.SalesOrderService;
import com.leo.erp.sales.order.web.dto.SalesOrderPrintXlsxRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.system.operationlog.support.OperationLogResultCollector;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderControllerTest {

    private final SalesOrderService service = mock(SalesOrderService.class);
    private final SalesOrderPrintExportService printExportService = mock(SalesOrderPrintExportService.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final SalesOrderController controller = new SalesOrderController(service, printExportService);

    @Test
    void searchReturnsSalesOrderList() {
        SalesOrderResponse order = mock(SalesOrderResponse.class);
        when(service.search("test", 100)).thenReturn(List.of(order));

        ApiResponse<List<SalesOrderResponse>> response = controller.search("test", 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).containsExactly(order);
        verify(service).search("test", 100);
    }

    @Test
    void searchWithNullKeywordUsesEmptyString() {
        when(service.search("", 100)).thenReturn(List.of());

        ApiResponse<List<SalesOrderResponse>> response = controller.search(null, 100);

        assertThat(response.data()).isEmpty();
        verify(service).search("", 100);
    }

    @Test
    void searchLimitsMaxTo500() {
        when(service.search("test", 500)).thenReturn(List.of());

        controller.search("test", 1000);

        verify(service).search("test", 500);
    }

    @Test
    void pageReturnsPaginatedSalesOrders() {
        SalesOrderResponse order = mock(SalesOrderResponse.class);
        Page<SalesOrderResponse> page = new PageImpl<>(List.of(order));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(service.page(any(), any())).thenReturn(page);

        ApiResponse<PageResponse<SalesOrderResponse>> response = controller.page(
                query, "test", null, "customer", null, "project", 7L, "active", null, null
        );

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void pagePassesStableCustomerAndProjectIds() throws Exception {
        PageQuery query = new PageQuery(0, 20, null, null);
        when(service.page(any(), any())).thenReturn(Page.empty());
        Method method = identityAwarePageMethod("page");

        assertThat(method).as("销售订单列表接口应接收 customerId/projectId").isNotNull();
        method.invoke(
                controller,
                query, "test", 101L, "customer", 102L, "project", 7L, "active", null, null
        );

        ArgumentCaptor<PageFilter> filterCaptor = ArgumentCaptor.forClass(PageFilter.class);
        verify(service).page(eq(query), filterCaptor.capture());
        assertThat(filterCaptor.getValue().customerId()).isEqualTo(101L);
        assertThat(filterCaptor.getValue().projectId()).isEqualTo(102L);
    }

    @Test
    void outboundImportCandidatesReturnsPaginatedSalesOrders() {
        SalesOrderResponse order = mock(SalesOrderResponse.class);
        Page<SalesOrderResponse> page = new PageImpl<>(List.of(order));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(service.outboundImportCandidates(any(), any())).thenReturn(page);

        ApiResponse<PageResponse<SalesOrderResponse>> response = controller.outboundImportCandidates(
                query, "test", null, "customer", null, "project", 7L, "active", null, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
        ArgumentCaptor<PageFilter> filterCaptor = ArgumentCaptor.forClass(PageFilter.class);
        verify(service).outboundImportCandidates(eq(query), filterCaptor.capture());
        assertThat(filterCaptor.getValue().name()).isEqualTo("customer");
        assertThat(filterCaptor.getValue().projectName()).isEqualTo("project");
        assertThat(filterCaptor.getValue().settlementCompanyId()).isEqualTo(7L);
    }

    @Test
    void outboundImportCandidatesPassStableCustomerAndProjectIds() throws Exception {
        PageQuery query = new PageQuery(0, 20, null, null);
        when(service.outboundImportCandidates(any(), any())).thenReturn(Page.empty());
        Method method = identityAwarePageMethod("outboundImportCandidates");

        assertThat(method).as("销售订单出库候选接口应接收 customerId/projectId").isNotNull();
        method.invoke(
                controller,
                query, "test", 101L, "customer", 102L, "project", 7L, "active", null, null
        );

        ArgumentCaptor<PageFilter> filterCaptor = ArgumentCaptor.forClass(PageFilter.class);
        verify(service).outboundImportCandidates(eq(query), filterCaptor.capture());
        assertThat(filterCaptor.getValue().customerId()).isEqualTo(101L);
        assertThat(filterCaptor.getValue().projectId()).isEqualTo(102L);
    }

    @Test
    void detailReturnsSalesOrderById() {
        SalesOrderResponse order = mock(SalesOrderResponse.class);
        when(service.detail(1L)).thenReturn(order);

        ApiResponse<SalesOrderResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(order);
    }

    @Test
    void exportPrintXlsxReturnsDownloadResponse() {
        byte[] content = new byte[]{1, 2, 3};
        when(printExportService.exportSalesOrderPrint(1L, SalesOrderPrintXlsxOptions.defaults())).thenReturn(new FileDownloadResponse(
                "SO-001-套打.xlsx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                content
        ));

        var response = controller.exportPrintXlsx(1L, request);

        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("SO-001-套打.xlsx");
        assertThat(response.getBody()).isEqualTo(content);
        verify(printExportService).exportSalesOrderPrint(1L, SalesOrderPrintXlsxOptions.defaults());
        verify(request).setAttribute(OperationLogResultCollector.BUSINESS_NO_ATTRIBUTE, null);
        verify(request).setAttribute(OperationLogResultCollector.RECORD_ID_ATTRIBUTE, null);
        verify(request).setAttribute(OperationLogResultCollector.MODULE_KEY_ATTRIBUTE, null);
    }

    @Test
    void exportPrintXlsxPassesPrintOptions() {
        byte[] content = new byte[]{1, 2, 3};
        SalesOrderPrintXlsxOptions options = new SalesOrderPrintXlsxOptions(true, true, "", Map.of(), Map.of("11", "抚新"), List.of("12", "11"));
        when(printExportService.exportSalesOrderPrint(1L, options)).thenReturn(new FileDownloadResponse(
                "SO-001-套打.xlsx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                content
        ));

        var response = controller.exportPrintXlsx(
                1L,
                new SalesOrderPrintXlsxRequest(new SalesOrderPrintXlsxOptions(
                        true,
                        true,
                        "",
                        Map.of(),
                        Map.of("11", " 抚新 "),
                        List.of("12", "11")
                )),
                request
        );

        assertThat(response.getBody()).isEqualTo(content);
        verify(printExportService).exportSalesOrderPrint(1L, options);
    }

    @Test
    void exportPrintXlsxPostUsesDefaultOptionsWhenPayloadIsNull() {
        byte[] content = new byte[]{1, 2, 3};
        when(printExportService.exportSalesOrderPrint(eq(1L), any())).thenReturn(new FileDownloadResponse(
                "SO-001-套打.xlsx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                content
        ));

        var response = controller.exportPrintXlsx(1L, null, request);

        assertThat(response.getBody()).isEqualTo(content);
        ArgumentCaptor<SalesOrderPrintXlsxOptions> optionsCaptor = ArgumentCaptor.forClass(SalesOrderPrintXlsxOptions.class);
        verify(printExportService).exportSalesOrderPrint(eq(1L), optionsCaptor.capture());
        assertThat(optionsCaptor.getValue()).isEqualTo(SalesOrderPrintXlsxOptions.defaults());
    }

    @Test
    void exportPrintXlsxHasOperationLogAnnotation() throws Exception {
        OperationLoggable annotation = SalesOrderController.class
                .getMethod("exportPrintXlsx", Long.class, SalesOrderPrintXlsxRequest.class, HttpServletRequest.class)
                .getAnnotation(OperationLoggable.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.moduleName()).isEqualTo("销售订单");
        assertThat(annotation.actionType()).isEqualTo("打印");
        assertThat(annotation.businessNoFields()).containsExactly("id");
        assertThat(annotation.recordIdField()).isEqualTo("id");
    }

    @Test
    void createReturnsCreatedSalesOrder() {
        SalesOrderRequest request = mock(SalesOrderRequest.class);
        SalesOrderResponse created = mock(SalesOrderResponse.class);
        when(service.create(request)).thenReturn(created);

        ApiResponse<SalesOrderResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(service).create(request);
    }

    @Test
    void updateReturnsUpdatedSalesOrder() {
        SalesOrderRequest request = mock(SalesOrderRequest.class);
        SalesOrderResponse updated = mock(SalesOrderResponse.class);
        when(service.update(1L, request)).thenReturn(updated);

        ApiResponse<SalesOrderResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(service).update(1L, request);
    }

    @Test
    void updateStatusReturnsUpdatedSalesOrder() {
        StatusUpdateRequest request = new StatusUpdateRequest("approved");
        SalesOrderResponse updated = mock(SalesOrderResponse.class);
        when(service.updateStatus(1L, "approved")).thenReturn(updated);

        ApiResponse<SalesOrderResponse> response = controller.updateStatus(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("状态更新成功");
        verify(service).updateStatus(1L, "approved");
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(service).delete(1L);
    }

    private Method identityAwarePageMethod(String name) {
        Class<?>[] parameterTypes = {
                PageQuery.class,
                String.class,
                Long.class,
                String.class,
                Long.class,
                String.class,
                Long.class,
                String.class,
                LocalDate.class,
                LocalDate.class
        };
        return Arrays.stream(SalesOrderController.class.getMethods())
                .filter(method -> method.getName().equals(name))
                .filter(method -> Arrays.equals(method.getParameterTypes(), parameterTypes))
                .findFirst()
                .orElse(null);
    }
}
