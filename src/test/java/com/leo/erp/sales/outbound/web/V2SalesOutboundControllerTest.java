package com.leo.erp.sales.outbound.web;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.sales.outbound.service.SalesOutboundService;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
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
 * V2SalesOutboundController 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class V2SalesOutboundControllerTest {

    @Mock
    private SalesOutboundService service;

    @InjectMocks
    private V2SalesOutboundController controller;

    @BeforeEach
    void setUpServletContext() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void clearServletContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private SalesOutboundRequest request() {
        return new SalesOutboundRequest(
                "OB001", "SO001", 10L, "客户A", 20L, "项目A", 1L, "库房A",
                LocalDate.of(2026, 8, 1), "DRAFT", null, List.of());
    }

    @Test
    void search_shouldNormalizeNullKeywordAndCapLimit() {
        when(service.search("", 100)).thenReturn(List.of(mock(SalesOutboundResponse.class)));

        var result = controller.search(null, 100);

        assertThat(result).hasSize(1);
        verify(service).search("", 100);
    }

    @Test
    void search_shouldCapLimitAt500() {
        when(service.search("kw", 500)).thenReturn(List.of());

        controller.search("kw", 1000);

        verify(service).search("kw", 500);
    }

    @Test
    void page_shouldDelegateWithFilter() {
        when(service.page(any(PageQuery.class), any(PageFilter.class), anyString())).thenReturn(
                org.mockito.Mockito.mock(org.springframework.data.domain.Page.class));

        PageQuery query = mock(PageQuery.class);
        var result = controller.page(query, "kw", 10L, "客户A", 20L, "项目A", 30L, "M001", "DRAFT",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(result).isNotNull();
        verify(service).page(any(PageQuery.class), any(PageFilter.class), anyString());
    }

    @Test
    void detail_shouldDelegate() {
        SalesOutboundResponse response = mock(SalesOutboundResponse.class);
        when(service.detail(5L)).thenReturn(response);

        assertThat(controller.detail(5L)).isSameAs(response);
    }

    @Test
    void create_shouldReturnCreatedWithLocation() {
        SalesOutboundResponse response = mock(SalesOutboundResponse.class);
        when(service.create(any())).thenReturn(response);
        SalesOutboundRequest req = request();

        ResponseEntity<SalesOutboundResponse> result = controller.create(req);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void createAndAudit_shouldReturnCreated() {
        SalesOutboundResponse response = mock(SalesOutboundResponse.class);
        when(service.createAndAudit(any())).thenReturn(response);

        ResponseEntity<SalesOutboundResponse> result = controller.createAndAudit(request());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void update_shouldDelegate() {
        SalesOutboundResponse response = mock(SalesOutboundResponse.class);
        when(service.update(anyLong(), any())).thenReturn(response);

        assertThat(controller.update(5L, request())).isSameAs(response);
    }

    @Test
    void updateAndAudit_shouldDelegate() {
        SalesOutboundResponse response = mock(SalesOutboundResponse.class);
        when(service.updateAndAudit(anyLong(), any())).thenReturn(response);

        assertThat(controller.updateAndAudit(5L, request())).isSameAs(response);
    }

    @Test
    void updateStatus_shouldPassStatusFromRequest() {
        SalesOutboundResponse response = mock(SalesOutboundResponse.class);
        when(service.updateStatus(anyLong(), anyString())).thenReturn(response);
        StatusUpdateRequest statusReq = new StatusUpdateRequest("AUDITED");

        assertThat(controller.updateStatus(5L, statusReq)).isSameAs(response);
        verify(service).updateStatus(5L, "AUDITED");
    }

    @Test
    void delete_shouldReturnNoContent() {
        org.mockito.Mockito.doNothing().when(service).delete(anyLong());

        ResponseEntity<Void> result = controller.delete(5L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(5L);
    }
}
