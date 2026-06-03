package com.leo.erp.sales.order.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.sales.order.service.SalesOrderService;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderControllerTest {

    private final SalesOrderService service = mock(SalesOrderService.class);
    private final SalesOrderController controller = new SalesOrderController(service);

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

        ApiResponse<PageResponse<SalesOrderResponse>> response = controller.page(query, "test", "customer", "project", "active", null, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
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
}