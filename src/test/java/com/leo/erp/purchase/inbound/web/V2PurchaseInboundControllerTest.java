package com.leo.erp.purchase.inbound.web;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.purchase.inbound.service.PurchaseInboundService;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundResponse;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V2PurchaseInboundController 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class V2PurchaseInboundControllerTest {

    @Mock
    private PurchaseInboundService service;

    @InjectMocks
    private V2PurchaseInboundController controller;

    @BeforeEach
    void setUpServletContext() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void clearServletContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private PurchaseInboundRequest request() {
        return new PurchaseInboundRequest(
                "IB001", "PO001", 10L, "S001", "供应商A", 1L, "库房A",
                LocalDate.of(2026, 8, 1), "MONTHLY", "DRAFT", null, List.of(), false);
    }

    @Test
    void search_shouldNormalizeNullKeywordAndCapLimit() {
        when(service.search("", 100)).thenReturn(List.of(mock(PurchaseInboundResponse.class)));

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
        when(service.page(any(PageQuery.class), any(PageFilter.class))).thenReturn(
                org.mockito.Mockito.mock(org.springframework.data.domain.Page.class));

        PageQuery query = mock(PageQuery.class);
        var result = controller.page(query, "kw", 10L, "供应商A", 30L, "DRAFT",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(result).isNotNull();
        verify(service).page(any(PageQuery.class), any(PageFilter.class));
    }

    @Test
    void detail_shouldDelegate() {
        PurchaseInboundResponse response = mock(PurchaseInboundResponse.class);
        when(service.detail(5L)).thenReturn(response);

        assertThat(controller.detail(5L)).isSameAs(response);
    }

    @Test
    void create_shouldReturnCreatedWithLocation() {
        PurchaseInboundResponse response = mock(PurchaseInboundResponse.class);
        when(service.create(any())).thenReturn(response);

        ResponseEntity<PurchaseInboundResponse> result = controller.create(request());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void update_shouldDelegate() {
        PurchaseInboundResponse response = mock(PurchaseInboundResponse.class);
        when(service.update(anyLong(), any())).thenReturn(response);

        assertThat(controller.update(5L, request())).isSameAs(response);
    }

    @Test
    void createAudit_shouldAuditExistingWhenBodyAbsent() {
        PurchaseInboundResponse response = mock(PurchaseInboundResponse.class);
        when(service.audit(5L)).thenReturn(response);

        ResponseEntity<PurchaseInboundResponse> result = controller.createAudit(5L, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(response);
        verify(service).audit(5L);
        verify(service, never()).updateAndAudit(anyLong(), any());
    }

    @Test
    void createAudit_shouldSaveAndAuditWhenBodyPresent() {
        PurchaseInboundResponse response = mock(PurchaseInboundResponse.class);
        when(service.updateAndAudit(anyLong(), any())).thenReturn(response);

        ResponseEntity<PurchaseInboundResponse> result = controller.createAudit(5L, request());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(response);
        verify(service).updateAndAudit(5L, request());
        verify(service, never()).audit(anyLong());
    }

    @Test
    void updateStatus_shouldPassStatusFromRequest() {
        PurchaseInboundResponse response = mock(PurchaseInboundResponse.class);
        when(service.updateStatus(anyLong(), any())).thenReturn(response);
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
