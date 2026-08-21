package com.leo.erp.logistics.bill.web;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.logistics.bill.service.FreightBillSalesOrderCandidateService;
import com.leo.erp.logistics.bill.service.FreightBillService;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
import com.leo.erp.logistics.bill.web.dto.FreightBillSalesOrderCandidateResponse;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V2FreightBillController 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class V2FreightBillControllerTest {

    @Mock
    private FreightBillService service;

    @Mock
    private FreightBillSalesOrderCandidateService candidateService;

    @Mock
    private FreightBillSalesOrderCandidateResponseAssembler candidateResponseAssembler;

    @InjectMocks
    private V2FreightBillController controller;

    @BeforeEach
    void setUpServletContext() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void clearServletContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private FreightBillRequest request() {
        return new FreightBillRequest(
                "FB001", 1L, "C001", "承运商A", 30L, "结算公司A", null, null,
                LocalDate.of(2026, 8, 1), new BigDecimal("100"), "DRAFT", null, List.of(), false);
    }

    @Test
    void salesOrderCandidates_shouldDelegate() {
        @SuppressWarnings("unchecked")
        PageResponse<FreightBillSalesOrderCandidateResponse> expected =
                (PageResponse<FreightBillSalesOrderCandidateResponse>) mock(PageResponse.class);
        @SuppressWarnings("unchecked")
        PageResponse<com.leo.erp.sales.api.SalesOrderSourceSnapshot> page =
                (PageResponse<com.leo.erp.sales.api.SalesOrderSourceSnapshot>) mock(PageResponse.class);
        when(candidateService.page(any(PageQuery.class), any(PageFilter.class))).thenReturn(page);
        when(candidateResponseAssembler.toPageResponse(any(PageResponse.class))).thenReturn(expected);

        PageResponse<FreightBillSalesOrderCandidateResponse> result = controller.salesOrderCandidates(
                mock(PageQuery.class), "kw", 10L, "客户A", 20L, "项目A", 30L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 5L);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void search_shouldCapLimit() {
        when(service.search("", 500)).thenReturn(List.of(mock(FreightBillResponse.class)));

        var result = controller.search(null, 1000);

        assertThat(result).hasSize(1);
        verify(service).search("", 500);
    }

    @Test
    void page_shouldDelegateWithCarrierCode() {
        when(service.page(any(PageQuery.class), any(PageFilter.class), anyString()))
                .thenReturn(mock(org.springframework.data.domain.Page.class));

        var result = controller.page(mock(PageQuery.class), "kw", 100L, "C001", "承运商A", 30L,
                "DRAFT", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(result).isNotNull();
        verify(service).page(any(PageQuery.class), any(PageFilter.class), anyString());
    }

    @Test
    void detail_shouldDelegate() {
        FreightBillResponse response = mock(FreightBillResponse.class);
        when(service.detail(5L)).thenReturn(response);

        assertThat(controller.detail(5L)).isSameAs(response);
    }

    @Test
    void create_shouldReturnCreated() {
        FreightBillResponse response = mock(FreightBillResponse.class);
        when(service.create(any())).thenReturn(response);

        ResponseEntity<FreightBillResponse> result = controller.create(request());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void update_shouldDelegate() {
        FreightBillResponse response = mock(FreightBillResponse.class);
        when(service.update(anyLong(), any())).thenReturn(response);

        assertThat(controller.update(5L, request())).isSameAs(response);
    }

    @Test
    void updateStatus_shouldPassStatus() {
        FreightBillResponse response = mock(FreightBillResponse.class);
        when(service.updateStatus(anyLong(), anyString())).thenReturn(response);

        assertThat(controller.updateStatus(5L, new StatusUpdateRequest("AUDITED"))).isSameAs(response);
        verify(service).updateStatus(5L, "AUDITED");
    }

    @Test
    void delete_shouldReturnNoContent() {
        org.mockito.Mockito.doNothing().when(service).delete(anyLong());

        ResponseEntity<Void> result = controller.delete(5L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
