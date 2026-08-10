package com.leo.erp.statement.freight.web;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.statement.freight.service.FreightStatementService;
import com.leo.erp.statement.freight.web.dto.FreightStatementCandidateResponse;
import com.leo.erp.statement.freight.web.dto.FreightStatementItemRequest;
import com.leo.erp.statement.freight.web.dto.FreightStatementRequest;
import com.leo.erp.statement.freight.web.dto.FreightStatementResponse;
import com.leo.erp.statement.freight.web.dto.FreightStatementSummaryResponse;
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
 * V2FreightStatementController 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class V2FreightStatementControllerTest {

    @Mock
    private FreightStatementService freightStatementService;

    @InjectMocks
    private V2FreightStatementController controller;

    @BeforeEach
    void setUpServletContext() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void clearServletContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private FreightStatementRequest request() {
        return new FreightStatementRequest(
                "FS001", "C001", "承运商A", 30L, "结算公司A", null, null, null, null,
                null, null, "DRAFT", null, null, List.of(), 100L);
    }

    @Test
    void search_shouldCapLimit() {
        when(freightStatementService.responseSearch("", 500)).thenReturn(List.of(mock(FreightStatementResponse.class)));

        var result = controller.search(null, 1000);

        assertThat(result).hasSize(1);
        verify(freightStatementService).responseSearch("", 500);
    }

    @Test
    void page_shouldDelegate() {
        when(freightStatementService.responsePage(any(PageQuery.class), any(PageFilter.class), anyString()))
                .thenReturn(mock(org.springframework.data.domain.Page.class));

        var result = controller.page(mock(PageQuery.class), "kw", 100L, "C001", "承运商A", 30L,
                "DRAFT", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(result).isNotNull();
    }

    @Test
    void summary_shouldDelegate() {
        FreightStatementSummaryResponse summary = mock(FreightStatementSummaryResponse.class);
        when(freightStatementService.summary(any(PageFilter.class), anyString())).thenReturn(summary);

        assertThat(controller.summary("kw", 100L, "C001", "承运商A", 30L, "DRAFT",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))).isSameAs(summary);
    }

    @Test
    void candidates_shouldDelegate() {
        when(freightStatementService.candidatePage(any(PageQuery.class), any(PageFilter.class), anyString()))
                .thenReturn(mock(org.springframework.data.domain.Page.class));

        var result = controller.candidates(mock(PageQuery.class), "kw", 100L, "C001", "承运商A", 30L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 5L);

        assertThat(result).isNotNull();
    }

    @Test
    void detail_shouldDelegate() {
        FreightStatementResponse response = mock(FreightStatementResponse.class);
        when(freightStatementService.responseDetail(5L)).thenReturn(response);

        assertThat(controller.detail(5L)).isSameAs(response);
    }

    @Test
    void create_shouldReturnCreated() {
        FreightStatementResponse response = mock(FreightStatementResponse.class);
        when(freightStatementService.responseCreate(any())).thenReturn(response);

        ResponseEntity<FreightStatementResponse> result = controller.create(request());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void createAndAudit_shouldReturnCreated() {
        FreightStatementResponse response = mock(FreightStatementResponse.class);
        when(freightStatementService.responseCreateAndAudit(any())).thenReturn(response);

        ResponseEntity<FreightStatementResponse> result = controller.createAndAudit(request());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void update_shouldDelegate() {
        FreightStatementResponse response = mock(FreightStatementResponse.class);
        when(freightStatementService.responseUpdate(anyLong(), any())).thenReturn(response);

        assertThat(controller.update(5L, request())).isSameAs(response);
    }

    @Test
    void updateAndAudit_shouldDelegate() {
        FreightStatementResponse response = mock(FreightStatementResponse.class);
        when(freightStatementService.responseUpdateAndAudit(anyLong(), any())).thenReturn(response);

        assertThat(controller.updateAndAudit(5L, request())).isSameAs(response);
    }

    @Test
    void updateStatus_shouldPassStatus() {
        FreightStatementResponse response = mock(FreightStatementResponse.class);
        when(freightStatementService.responseUpdateStatus(anyLong(), anyString())).thenReturn(response);

        assertThat(controller.updateStatus(5L, new StatusUpdateRequest("AUDITED"))).isSameAs(response);
        verify(freightStatementService).responseUpdateStatus(5L, "AUDITED");
    }

    @Test
    void delete_shouldReturnNoContent() {
        org.mockito.Mockito.doNothing().when(freightStatementService).delete(anyLong());

        ResponseEntity<Void> result = controller.delete(5L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
