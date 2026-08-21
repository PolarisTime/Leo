package com.leo.erp.statement.customer.web;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.statement.customer.service.CustomerStatementService;
import com.leo.erp.statement.customer.web.dto.CustomerStatementCandidateResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementSummaryResponse;
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
 * V2CustomerStatementController 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class V2CustomerStatementControllerTest {

    @Mock
    private CustomerStatementService customerStatementService;

    @InjectMocks
    private V2CustomerStatementController controller;

    @BeforeEach
    void setUpServletContext() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void clearServletContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private CustomerStatementRequest request() {
        return new CustomerStatementRequest(
                "CS001", null, "客户A", null, "项目A", null, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                new BigDecimal("5000"), null, null, "DRAFT", null, List.of(), null, false);
    }

    @Test
    void search_shouldCapLimit() {
        when(customerStatementService.search("", 500)).thenReturn(List.of(mock(CustomerStatementResponse.class)));

        var result = controller.search(null, 1000);

        assertThat(result).hasSize(1);
        verify(customerStatementService).search("", 500);
    }

    @Test
    void page_shouldDelegate() {
        when(customerStatementService.page(any(PageQuery.class), any(PageFilter.class)))
                .thenReturn(mock(org.springframework.data.domain.Page.class));

        var result = controller.page(mock(PageQuery.class), "kw", 10L, "客户A", 20L, "项目A", 30L,
                "DRAFT", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(result).isNotNull();
    }

    @Test
    void summary_shouldDelegate() {
        CustomerStatementSummaryResponse summary = mock(CustomerStatementSummaryResponse.class);
        when(customerStatementService.summary(any(PageFilter.class))).thenReturn(summary);

        assertThat(controller.summary("kw", 10L, "客户A", 20L, "项目A", 30L, "DRAFT",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))).isSameAs(summary);
    }

    @Test
    void candidates_shouldDelegate() {
        @SuppressWarnings("unchecked")
        PageResponse<CustomerStatementCandidateResponse> expected =
                (PageResponse<CustomerStatementCandidateResponse>) mock(PageResponse.class);
        when(customerStatementService.candidatePage(any(PageQuery.class), any(PageFilter.class)))
                .thenReturn(mock(org.springframework.data.domain.Page.class));

        var result = controller.candidates(mock(PageQuery.class), "kw", 10L, "客户A", 20L, "项目A", 30L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 5L);

        assertThat(result).isNotNull();
    }

    @Test
    void detail_shouldDelegate() {
        CustomerStatementResponse response = mock(CustomerStatementResponse.class);
        when(customerStatementService.detail(5L)).thenReturn(response);

        assertThat(controller.detail(5L)).isSameAs(response);
    }

    @Test
    void create_shouldReturnCreated() {
        CustomerStatementResponse response = mock(CustomerStatementResponse.class);
        when(customerStatementService.create(any())).thenReturn(response);

        ResponseEntity<CustomerStatementResponse> result = controller.create(request());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void update_shouldDelegate() {
        CustomerStatementResponse response = mock(CustomerStatementResponse.class);
        when(customerStatementService.update(anyLong(), any())).thenReturn(response);

        assertThat(controller.update(5L, request())).isSameAs(response);
    }

    @Test
    void updateStatus_shouldPassStatus() {
        CustomerStatementResponse response = mock(CustomerStatementResponse.class);
        when(customerStatementService.updateStatus(anyLong(), anyString())).thenReturn(response);

        assertThat(controller.updateStatus(5L, new StatusUpdateRequest("CONFIRMED"))).isSameAs(response);
        verify(customerStatementService).updateStatus(5L, "CONFIRMED");
    }

    @Test
    void delete_shouldReturnNoContent() {
        org.mockito.Mockito.doNothing().when(customerStatementService).delete(anyLong());

        ResponseEntity<Void> result = controller.delete(5L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
