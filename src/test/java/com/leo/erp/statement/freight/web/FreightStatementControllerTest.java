package com.leo.erp.statement.freight.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.statement.freight.service.FreightStatementService;
import com.leo.erp.statement.freight.web.dto.FreightStatementCandidateResponse;
import com.leo.erp.statement.freight.web.dto.FreightStatementRequest;
import com.leo.erp.statement.freight.web.dto.FreightStatementResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FreightStatementControllerTest {

    private final FreightStatementService freightStatementService = mock(FreightStatementService.class);
    private final FreightStatementController controller = new FreightStatementController(freightStatementService);

    @Test
    void searchReturnsList() {
        FreightStatementResponse item = mock(FreightStatementResponse.class);
        when(freightStatementService.responseSearch(eq("test"), eq(100))).thenReturn(List.of(item));

        ApiResponse<List<FreightStatementResponse>> response = controller.search("test", 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void searchWithNullKeywordDefaultsToEmpty() {
        FreightStatementResponse item = mock(FreightStatementResponse.class);
        when(freightStatementService.responseSearch(eq(""), eq(100))).thenReturn(List.of(item));

        ApiResponse<List<FreightStatementResponse>> response = controller.search(null, 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
        verify(freightStatementService).responseSearch("", 100);
    }

    @Test
    void pageReturnsPaginatedStatements() {
        FreightStatementResponse item = mock(FreightStatementResponse.class);
        Page<FreightStatementResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(freightStatementService.responsePage(any(), any(), any())).thenReturn(page);

        ApiResponse<PageResponse<FreightStatementResponse>> response = controller.page(
                query, "test", 101L, "CR-001", "carrier", 7L, "active", null, null, null
        );

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
        verify(freightStatementService).responsePage(eq(query), any(PageFilter.class), eq("CR-001"));
    }

    @Test
    void candidatesReturnsPaginatedCandidates() {
        FreightStatementCandidateResponse item = mock(FreightStatementCandidateResponse.class);
        Page<FreightStatementCandidateResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(freightStatementService.candidatePage(any(), any(), any())).thenReturn(page);

        ApiResponse<PageResponse<FreightStatementCandidateResponse>> response = controller.candidates(
                query, "test", 101L, "CR-001", "carrier", 7L, null, null, 201L
        );

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
        ArgumentCaptor<PageFilter> filterCaptor = ArgumentCaptor.forClass(PageFilter.class);
        verify(freightStatementService).candidatePage(eq(query), filterCaptor.capture(), eq("CR-001"));
        assertThat(filterCaptor.getValue().carrierId()).isEqualTo(101L);
        assertThat(filterCaptor.getValue().currentRecordId()).isEqualTo(201L);
    }

    @Test
    void detailReturnsStatementById() {
        FreightStatementResponse statement = mock(FreightStatementResponse.class);
        when(freightStatementService.responseDetail(1L)).thenReturn(statement);

        ApiResponse<FreightStatementResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(statement);
    }

    @Test
    void createReturnsCreatedStatement() {
        FreightStatementRequest request = mock(FreightStatementRequest.class);
        FreightStatementResponse created = mock(FreightStatementResponse.class);
        when(freightStatementService.responseCreate(request)).thenReturn(created);

        ApiResponse<FreightStatementResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(freightStatementService).responseCreate(request);
    }

    @Test
    void updateReturnsUpdatedStatement() {
        FreightStatementRequest request = mock(FreightStatementRequest.class);
        FreightStatementResponse updated = mock(FreightStatementResponse.class);
        when(freightStatementService.responseUpdate(1L, request)).thenReturn(updated);

        ApiResponse<FreightStatementResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(freightStatementService).responseUpdate(1L, request);
    }

    @Test
    void updateStatusReturnsUpdatedStatement() {
        StatusUpdateRequest request = new StatusUpdateRequest("approved");
        FreightStatementResponse updated = mock(FreightStatementResponse.class);
        when(freightStatementService.responseUpdateStatus(1L, "approved")).thenReturn(updated);

        ApiResponse<FreightStatementResponse> response = controller.updateStatus(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("状态更新成功");
        verify(freightStatementService).responseUpdateStatus(1L, "approved");
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(freightStatementService).delete(1L);
    }
}
