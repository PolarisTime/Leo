package com.leo.erp.statement.customer.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.statement.customer.service.CustomerStatementService;
import com.leo.erp.statement.customer.web.dto.CustomerStatementCandidateResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
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

class CustomerStatementControllerTest {

    private final CustomerStatementService customerStatementService = mock(CustomerStatementService.class);
    private final CustomerStatementController controller = new CustomerStatementController(customerStatementService);

    @Test
    void searchReturnsList() {
        CustomerStatementResponse item = mock(CustomerStatementResponse.class);
        when(customerStatementService.search(eq("test"), eq(100))).thenReturn(List.of(item));

        ApiResponse<List<CustomerStatementResponse>> response = controller.search("test", 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void searchWithNullKeywordDefaultsToEmpty() {
        CustomerStatementResponse item = mock(CustomerStatementResponse.class);
        when(customerStatementService.search(eq(""), eq(100))).thenReturn(List.of(item));

        ApiResponse<List<CustomerStatementResponse>> response = controller.search(null, 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
        verify(customerStatementService).search("", 100);
    }

    @Test
    void pageReturnsPaginatedStatements() {
        CustomerStatementResponse item = mock(CustomerStatementResponse.class);
        Page<CustomerStatementResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(customerStatementService.page(any(), any())).thenReturn(page);

        ApiResponse<PageResponse<CustomerStatementResponse>> response = controller.page(query, "test", "customer", "active", null, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void candidatesReturnsPaginatedCandidates() {
        CustomerStatementCandidateResponse item = mock(CustomerStatementCandidateResponse.class);
        Page<CustomerStatementCandidateResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(customerStatementService.candidatePage(any(), any())).thenReturn(page);

        ApiResponse<PageResponse<CustomerStatementCandidateResponse>> response = controller.candidates(query, "test", "customer", "project", null, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsStatementById() {
        CustomerStatementResponse statement = mock(CustomerStatementResponse.class);
        when(customerStatementService.detail(1L)).thenReturn(statement);

        ApiResponse<CustomerStatementResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(statement);
    }

    @Test
    void createReturnsCreatedStatement() {
        CustomerStatementRequest request = mock(CustomerStatementRequest.class);
        CustomerStatementResponse created = mock(CustomerStatementResponse.class);
        when(customerStatementService.create(request)).thenReturn(created);

        ApiResponse<CustomerStatementResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(customerStatementService).create(request);
    }

    @Test
    void updateReturnsUpdatedStatement() {
        CustomerStatementRequest request = mock(CustomerStatementRequest.class);
        CustomerStatementResponse updated = mock(CustomerStatementResponse.class);
        when(customerStatementService.update(1L, request)).thenReturn(updated);

        ApiResponse<CustomerStatementResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(customerStatementService).update(1L, request);
    }

    @Test
    void updateStatusReturnsUpdatedStatement() {
        StatusUpdateRequest request = new StatusUpdateRequest("已确认");
        CustomerStatementResponse updated = mock(CustomerStatementResponse.class);
        when(customerStatementService.updateStatus(1L, "已确认")).thenReturn(updated);

        ApiResponse<CustomerStatementResponse> response = controller.updateStatus(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("状态更新成功");
        verify(customerStatementService).updateStatus(1L, "已确认");
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(customerStatementService).delete(1L);
    }
}
