package com.leo.erp.finance.invoiceissue.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.finance.invoiceissue.service.InvoiceIssueCandidateService;
import com.leo.erp.finance.invoiceissue.service.InvoiceIssueService;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueRequest;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueResponse;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueSourceCandidateResponse;
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

class InvoiceIssueControllerTest {

    private final InvoiceIssueService service = mock(InvoiceIssueService.class);
    private final InvoiceIssueCandidateService candidateService = mock(InvoiceIssueCandidateService.class);
    private final InvoiceIssueController controller = new InvoiceIssueController(service, candidateService);

    @Test
    void searchReturnsInvoiceIssueList() {
        InvoiceIssueResponse invoice = mock(InvoiceIssueResponse.class);
        when(service.search("test", 100)).thenReturn(List.of(invoice));

        ApiResponse<List<InvoiceIssueResponse>> response = controller.search("test", 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).containsExactly(invoice);
        verify(service).search("test", 100);
    }

    @Test
    void searchWithNullKeywordUsesEmptyString() {
        when(service.search("", 100)).thenReturn(List.of());

        ApiResponse<List<InvoiceIssueResponse>> response = controller.search(null, 100);

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
    void pageReturnsPaginatedInvoiceIssues() {
        InvoiceIssueResponse invoice = mock(InvoiceIssueResponse.class);
        Page<InvoiceIssueResponse> page = new PageImpl<>(List.of(invoice));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(service.page(any(), any())).thenReturn(page);

        ApiResponse<PageResponse<InvoiceIssueResponse>> response = controller.page(query, "test", "customer", null, "active", null, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void sourceCandidatesPassStableFiltersAndCurrentIssueId() {
        InvoiceIssueSourceCandidateResponse candidate = mock(InvoiceIssueSourceCandidateResponse.class);
        PageQuery query = PageQuery.of(0, 15, null, null);
        when(candidateService.sourceCandidates(any(), any()))
                .thenReturn(new PageImpl<>(List.of(candidate)));

        ApiResponse<PageResponse<InvoiceIssueSourceCandidateResponse>> response = controller.sourceCandidates(
                query,
                "SO-001",
                501L,
                "客户A",
                601L,
                "项目A",
                88L,
                null,
                null,
                null,
                9001L
        );

        assertThat(response.data().content()).containsExactly(candidate);
        ArgumentCaptor<PageFilter> filterCaptor = ArgumentCaptor.forClass(PageFilter.class);
        verify(candidateService).sourceCandidates(eq(query), filterCaptor.capture());
        assertThat(filterCaptor.getValue().customerId()).isEqualTo(501L);
        assertThat(filterCaptor.getValue().projectId()).isEqualTo(601L);
        assertThat(filterCaptor.getValue().settlementCompanyId()).isEqualTo(88L);
        assertThat(filterCaptor.getValue().currentRecordId()).isEqualTo(9001L);
    }

    @Test
    void detailReturnsInvoiceIssueById() {
        InvoiceIssueResponse invoice = mock(InvoiceIssueResponse.class);
        when(service.detail(1L)).thenReturn(invoice);

        ApiResponse<InvoiceIssueResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(invoice);
    }

    @Test
    void createReturnsCreatedInvoiceIssue() {
        InvoiceIssueRequest request = mock(InvoiceIssueRequest.class);
        InvoiceIssueResponse created = mock(InvoiceIssueResponse.class);
        when(service.create(request)).thenReturn(created);

        ApiResponse<InvoiceIssueResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(service).create(request);
    }

    @Test
    void updateReturnsUpdatedInvoiceIssue() {
        InvoiceIssueRequest request = mock(InvoiceIssueRequest.class);
        InvoiceIssueResponse updated = mock(InvoiceIssueResponse.class);
        when(service.update(1L, request)).thenReturn(updated);

        ApiResponse<InvoiceIssueResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(service).update(1L, request);
    }

    @Test
    void updateStatusReturnsUpdatedInvoiceIssue() {
        StatusUpdateRequest request = new StatusUpdateRequest("approved");
        InvoiceIssueResponse updated = mock(InvoiceIssueResponse.class);
        when(service.updateStatus(1L, "approved")).thenReturn(updated);

        ApiResponse<InvoiceIssueResponse> response = controller.updateStatus(1L, request);

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
