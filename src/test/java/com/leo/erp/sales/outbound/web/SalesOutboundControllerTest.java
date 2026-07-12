package com.leo.erp.sales.outbound.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.sales.outbound.service.SalesOutboundService;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOutboundControllerTest {

    private final SalesOutboundService service = mock(SalesOutboundService.class);
    private final SalesOutboundController controller = new SalesOutboundController(service);

    @Test
    void searchReturnsSalesOutboundList() {
        SalesOutboundResponse outbound = mock(SalesOutboundResponse.class);
        when(service.search("test", 100)).thenReturn(List.of(outbound));

        ApiResponse<List<SalesOutboundResponse>> response = controller.search("test", 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).containsExactly(outbound);
        verify(service).search("test", 100);
    }

    @Test
    void searchWithNullKeywordUsesEmptyString() {
        when(service.search("", 100)).thenReturn(List.of());

        ApiResponse<List<SalesOutboundResponse>> response = controller.search(null, 100);

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
    void pageReturnsPaginatedSalesOutbounds() {
        SalesOutboundResponse outbound = mock(SalesOutboundResponse.class);
        Page<SalesOutboundResponse> page = new PageImpl<>(List.of(outbound));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(service.page(any(), any())).thenReturn(page);

        ApiResponse<PageResponse<SalesOutboundResponse>> response = controller.page(
                query, "test", null, "customer", null, "project", 7L, "active", null, null
        );

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void pagePassesStableCustomerAndProjectIds() throws Exception {
        PageQuery query = new PageQuery(0, 20, null, null);
        when(service.page(any(), any())).thenReturn(Page.empty());
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
        Method method = Arrays.stream(SalesOutboundController.class.getMethods())
                .filter(candidate -> candidate.getName().equals("page"))
                .filter(candidate -> Arrays.equals(candidate.getParameterTypes(), parameterTypes))
                .findFirst()
                .orElse(null);

        assertThat(method).as("销售出库列表接口应接收 customerId/projectId").isNotNull();
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
    void detailReturnsSalesOutboundById() {
        SalesOutboundResponse outbound = mock(SalesOutboundResponse.class);
        when(service.detail(1L)).thenReturn(outbound);

        ApiResponse<SalesOutboundResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(outbound);
    }

    @Test
    void createReturnsCreatedSalesOutbound() {
        SalesOutboundRequest request = mock(SalesOutboundRequest.class);
        SalesOutboundResponse created = mock(SalesOutboundResponse.class);
        when(service.create(request)).thenReturn(created);

        ApiResponse<SalesOutboundResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(service).create(request);
    }

    @Test
    void updateReturnsUpdatedSalesOutbound() {
        SalesOutboundRequest request = mock(SalesOutboundRequest.class);
        SalesOutboundResponse updated = mock(SalesOutboundResponse.class);
        when(service.update(1L, request)).thenReturn(updated);

        ApiResponse<SalesOutboundResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(service).update(1L, request);
    }

    @Test
    void updateStatusReturnsUpdatedSalesOutbound() {
        StatusUpdateRequest request = new StatusUpdateRequest("approved");
        SalesOutboundResponse updated = mock(SalesOutboundResponse.class);
        when(service.updateStatus(1L, "approved")).thenReturn(updated);

        ApiResponse<SalesOutboundResponse> response = controller.updateStatus(1L, request);

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
