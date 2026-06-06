package com.leo.erp.statement.supplier.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.statement.supplier.service.SupplierStatementService;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementCandidateResponse;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementRequest;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementResponse;
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

class SupplierStatementControllerTest {

    private final SupplierStatementService supplierStatementService = mock(SupplierStatementService.class);
    private final SupplierStatementController controller = new SupplierStatementController(supplierStatementService);

    @Test
    void searchReturnsList() {
        SupplierStatementResponse item = mock(SupplierStatementResponse.class);
        when(supplierStatementService.search(eq("test"), eq(100))).thenReturn(List.of(item));

        ApiResponse<List<SupplierStatementResponse>> response = controller.search("test", 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void searchWithNullKeywordDefaultsToEmpty() {
        SupplierStatementResponse item = mock(SupplierStatementResponse.class);
        when(supplierStatementService.search(eq(""), eq(100))).thenReturn(List.of(item));

        ApiResponse<List<SupplierStatementResponse>> response = controller.search(null, 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
        verify(supplierStatementService).search("", 100);
    }

    @Test
    void pageReturnsPaginatedStatements() {
        SupplierStatementResponse item = mock(SupplierStatementResponse.class);
        Page<SupplierStatementResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(supplierStatementService.page(any(), any())).thenReturn(page);

        ApiResponse<PageResponse<SupplierStatementResponse>> response = controller.page(query, "test", "supplier", "active", null, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void candidatesReturnsPaginatedCandidates() {
        SupplierStatementCandidateResponse item = mock(SupplierStatementCandidateResponse.class);
        Page<SupplierStatementCandidateResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(supplierStatementService.candidatePage(any(), any())).thenReturn(page);

        ApiResponse<PageResponse<SupplierStatementCandidateResponse>> response = controller.candidates(query, "test", "supplier", null, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsStatementById() {
        SupplierStatementResponse statement = mock(SupplierStatementResponse.class);
        when(supplierStatementService.detail(1L)).thenReturn(statement);

        ApiResponse<SupplierStatementResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(statement);
    }

    @Test
    void createReturnsCreatedStatement() {
        SupplierStatementRequest request = mock(SupplierStatementRequest.class);
        SupplierStatementResponse created = mock(SupplierStatementResponse.class);
        when(supplierStatementService.create(request)).thenReturn(created);

        ApiResponse<SupplierStatementResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(supplierStatementService).create(request);
    }

    @Test
    void updateReturnsUpdatedStatement() {
        SupplierStatementRequest request = mock(SupplierStatementRequest.class);
        SupplierStatementResponse updated = mock(SupplierStatementResponse.class);
        when(supplierStatementService.update(1L, request)).thenReturn(updated);

        ApiResponse<SupplierStatementResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(supplierStatementService).update(1L, request);
    }

    @Test
    void updateStatusReturnsUpdatedStatement() {
        StatusUpdateRequest request = new StatusUpdateRequest("已确认");
        SupplierStatementResponse updated = mock(SupplierStatementResponse.class);
        when(supplierStatementService.updateStatus(1L, "已确认")).thenReturn(updated);

        ApiResponse<SupplierStatementResponse> response = controller.updateStatus(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("状态更新成功");
        verify(supplierStatementService).updateStatus(1L, "已确认");
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(supplierStatementService).delete(1L);
    }
}
