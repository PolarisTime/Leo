package com.leo.erp.finance.receipt.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.finance.receipt.service.ReceiptService;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
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

class ReceiptControllerTest {

    private final ReceiptService receiptService = mock(ReceiptService.class);
    private final ReceiptController controller = new ReceiptController(receiptService);

    @Test
    void searchReturnsReceiptList() {
        ReceiptResponse receipt = mock(ReceiptResponse.class);
        when(receiptService.search("test", 100)).thenReturn(List.of(receipt));

        ApiResponse<List<ReceiptResponse>> response = controller.search("test", 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).containsExactly(receipt);
        verify(receiptService).search("test", 100);
    }

    @Test
    void searchWithNullKeywordUsesEmptyString() {
        when(receiptService.search("", 100)).thenReturn(List.of());

        ApiResponse<List<ReceiptResponse>> response = controller.search(null, 100);

        assertThat(response.data()).isEmpty();
        verify(receiptService).search("", 100);
    }

    @Test
    void searchLimitsMaxTo500() {
        when(receiptService.search("test", 500)).thenReturn(List.of());

        controller.search("test", 1000);

        verify(receiptService).search("test", 500);
    }

    @Test
    void pageReturnsPaginatedReceipts() {
        ReceiptResponse receipt = mock(ReceiptResponse.class);
        Page<ReceiptResponse> page = new PageImpl<>(List.of(receipt));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(receiptService.page(any(), any())).thenReturn(page);

        ApiResponse<PageResponse<ReceiptResponse>> response = controller.page(query, "test", "customer", null, "active", null, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsReceiptById() {
        ReceiptResponse receipt = mock(ReceiptResponse.class);
        when(receiptService.detail(1L)).thenReturn(receipt);

        ApiResponse<ReceiptResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(receipt);
    }

    @Test
    void createReturnsCreatedReceipt() {
        ReceiptRequest request = mock(ReceiptRequest.class);
        ReceiptResponse created = mock(ReceiptResponse.class);
        when(receiptService.create(request)).thenReturn(created);

        ApiResponse<ReceiptResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(receiptService).create(request);
    }

    @Test
    void updateReturnsUpdatedReceipt() {
        ReceiptRequest request = mock(ReceiptRequest.class);
        ReceiptResponse updated = mock(ReceiptResponse.class);
        when(receiptService.update(1L, request)).thenReturn(updated);

        ApiResponse<ReceiptResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(receiptService).update(1L, request);
    }

    @Test
    void updateStatusReturnsUpdatedReceipt() {
        StatusUpdateRequest request = new StatusUpdateRequest("approved");
        ReceiptResponse updated = mock(ReceiptResponse.class);
        when(receiptService.updateStatus(1L, "approved")).thenReturn(updated);

        ApiResponse<ReceiptResponse> response = controller.updateStatus(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("状态更新成功");
        verify(receiptService).updateStatus(1L, "approved");
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(receiptService).delete(1L);
    }
}
