package com.leo.erp.finance.invoicereceipt.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.finance.invoicereceipt.service.InvoiceReceiptCandidateService;
import com.leo.erp.finance.invoicereceipt.service.InvoiceReceiptService;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptRequest;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptResponse;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptSourceCandidateResponse;
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

class InvoiceReceiptControllerTest {

    private final InvoiceReceiptService service = mock(InvoiceReceiptService.class);
    private final InvoiceReceiptCandidateService candidateService = mock(InvoiceReceiptCandidateService.class);
    private final InvoiceReceiptController controller = new InvoiceReceiptController(service, candidateService);

    @Test
    void searchReturnsInvoiceReceiptList() {
        InvoiceReceiptResponse invoice = mock(InvoiceReceiptResponse.class);
        when(service.search("test", 100)).thenReturn(List.of(invoice));

        ApiResponse<List<InvoiceReceiptResponse>> response = controller.search("test", 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).containsExactly(invoice);
        verify(service).search("test", 100);
    }

    @Test
    void searchWithNullKeywordUsesEmptyString() {
        when(service.search("", 100)).thenReturn(List.of());

        ApiResponse<List<InvoiceReceiptResponse>> response = controller.search(null, 100);

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
    void pageReturnsPaginatedInvoiceReceipts() {
        InvoiceReceiptResponse invoice = mock(InvoiceReceiptResponse.class);
        Page<InvoiceReceiptResponse> page = new PageImpl<>(List.of(invoice));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(service.page(any(), any())).thenReturn(page);

        ApiResponse<PageResponse<InvoiceReceiptResponse>> response = controller.page(query, "test", "supplier", "active", null, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void sourceCandidatesPassStableFiltersAndCurrentReceiptId() {
        InvoiceReceiptSourceCandidateResponse candidate = mock(InvoiceReceiptSourceCandidateResponse.class);
        PageQuery query = PageQuery.of(0, 15, null, null);
        when(candidateService.sourceCandidates(any(), any()))
                .thenReturn(new PageImpl<>(List.of(candidate)));

        ApiResponse<PageResponse<InvoiceReceiptSourceCandidateResponse>> response = controller.sourceCandidates(
                query,
                "PO-001",
                701L,
                "供应商A",
                88L,
                null,
                null,
                null,
                9002L
        );

        assertThat(response.data().content()).containsExactly(candidate);
        ArgumentCaptor<PageFilter> filterCaptor = ArgumentCaptor.forClass(PageFilter.class);
        verify(candidateService).sourceCandidates(eq(query), filterCaptor.capture());
        assertThat(filterCaptor.getValue().supplierId()).isEqualTo(701L);
        assertThat(filterCaptor.getValue().settlementCompanyId()).isEqualTo(88L);
        assertThat(filterCaptor.getValue().currentRecordId()).isEqualTo(9002L);
    }

    @Test
    void detailReturnsInvoiceReceiptById() {
        InvoiceReceiptResponse invoice = mock(InvoiceReceiptResponse.class);
        when(service.detail(1L)).thenReturn(invoice);

        ApiResponse<InvoiceReceiptResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(invoice);
    }

    @Test
    void createReturnsCreatedInvoiceReceipt() {
        InvoiceReceiptRequest request = mock(InvoiceReceiptRequest.class);
        InvoiceReceiptResponse created = mock(InvoiceReceiptResponse.class);
        when(service.create(request)).thenReturn(created);

        ApiResponse<InvoiceReceiptResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(service).create(request);
    }

    @Test
    void updateReturnsUpdatedInvoiceReceipt() {
        InvoiceReceiptRequest request = mock(InvoiceReceiptRequest.class);
        InvoiceReceiptResponse updated = mock(InvoiceReceiptResponse.class);
        when(service.update(1L, request)).thenReturn(updated);

        ApiResponse<InvoiceReceiptResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(service).update(1L, request);
    }

    @Test
    void updateStatusReturnsUpdatedInvoiceReceipt() {
        StatusUpdateRequest request = new StatusUpdateRequest("approved");
        InvoiceReceiptResponse updated = mock(InvoiceReceiptResponse.class);
        when(service.updateStatus(1L, "approved")).thenReturn(updated);

        ApiResponse<InvoiceReceiptResponse> response = controller.updateStatus(1L, request);

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
