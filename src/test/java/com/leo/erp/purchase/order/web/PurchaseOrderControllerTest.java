package com.leo.erp.purchase.order.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.purchase.order.service.PurchaseOrderService;
import com.leo.erp.purchase.order.web.dto.PieceWeightResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderImportCandidateResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
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

class PurchaseOrderControllerTest {

    private final PurchaseOrderService purchaseOrderService = mock(PurchaseOrderService.class);
    private final PurchaseOrderController controller = new PurchaseOrderController(purchaseOrderService);

    @Test
    void searchReturnsPurchaseOrderList() {
        PurchaseOrderResponse order = mock(PurchaseOrderResponse.class);
        when(purchaseOrderService.search("test", 100)).thenReturn(List.of(order));

        ApiResponse<List<PurchaseOrderResponse>> response = controller.search("test", 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).containsExactly(order);
        verify(purchaseOrderService).search("test", 100);
    }

    @Test
    void searchWithNullKeywordUsesEmptyString() {
        when(purchaseOrderService.search("", 100)).thenReturn(List.of());

        ApiResponse<List<PurchaseOrderResponse>> response = controller.search(null, 100);

        assertThat(response.data()).isEmpty();
        verify(purchaseOrderService).search("", 100);
    }

    @Test
    void searchLimitsMaxTo500() {
        when(purchaseOrderService.search("test", 500)).thenReturn(List.of());

        controller.search("test", 1000);

        verify(purchaseOrderService).search("test", 500);
    }

    @Test
    void importCandidatesReturnsPaginatedCandidates() {
        PurchaseOrderImportCandidateResponse candidate = mock(PurchaseOrderImportCandidateResponse.class);
        Page<PurchaseOrderImportCandidateResponse> page = new PageImpl<>(List.of(candidate));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(purchaseOrderService.importCandidates(any(), any(), eq("usage"))).thenReturn(page);

        ApiResponse<PageResponse<PurchaseOrderImportCandidateResponse>> response = controller.importCandidates(
                query, "test", "supplier", 7L, "active", null, null, "usage"
        );

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
        ArgumentCaptor<PageFilter> filterCaptor = ArgumentCaptor.forClass(PageFilter.class);
        verify(purchaseOrderService).importCandidates(eq(query), filterCaptor.capture(), eq("usage"));
        assertThat(filterCaptor.getValue().settlementCompanyId()).isEqualTo(7L);
    }

    @Test
    void pageReturnsPaginatedPurchaseOrders() {
        PurchaseOrderResponse order = mock(PurchaseOrderResponse.class);
        Page<PurchaseOrderResponse> page = new PageImpl<>(List.of(order));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(purchaseOrderService.page(any(), any())).thenReturn(page);

        ApiResponse<PageResponse<PurchaseOrderResponse>> response = controller.page(query, "test", "supplier", 7L, "active", null, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsPurchaseOrderById() {
        PurchaseOrderResponse order = mock(PurchaseOrderResponse.class);
        when(purchaseOrderService.detail(1L)).thenReturn(order);

        ApiResponse<PurchaseOrderResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(order);
    }

    @Test
    void createReturnsCreatedPurchaseOrder() {
        PurchaseOrderRequest request = mock(PurchaseOrderRequest.class);
        PurchaseOrderResponse created = mock(PurchaseOrderResponse.class);
        when(purchaseOrderService.create(request)).thenReturn(created);

        ApiResponse<PurchaseOrderResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(purchaseOrderService).create(request);
    }

    @Test
    void updateReturnsUpdatedPurchaseOrder() {
        PurchaseOrderRequest request = mock(PurchaseOrderRequest.class);
        PurchaseOrderResponse updated = mock(PurchaseOrderResponse.class);
        when(purchaseOrderService.update(1L, request)).thenReturn(updated);

        ApiResponse<PurchaseOrderResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(purchaseOrderService).update(1L, request);
    }

    @Test
    void updateStatusReturnsUpdatedPurchaseOrder() {
        StatusUpdateRequest request = new StatusUpdateRequest("approved");
        PurchaseOrderResponse updated = mock(PurchaseOrderResponse.class);
        when(purchaseOrderService.updateStatus(1L, "approved")).thenReturn(updated);

        ApiResponse<PurchaseOrderResponse> response = controller.updateStatus(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("状态更新成功");
        verify(purchaseOrderService).updateStatus(1L, "approved");
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(purchaseOrderService).delete(1L);
    }

    @Test
    void pieceWeightsReturnsWeightsForItem() {
        PieceWeightResponse weight = mock(PieceWeightResponse.class);
        when(purchaseOrderService.getPieceWeights(1L)).thenReturn(List.of(weight));

        ApiResponse<List<PieceWeightResponse>> response = controller.pieceWeights(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).containsExactly(weight);
        verify(purchaseOrderService).getPieceWeights(1L);
    }

    @Test
    void pieceWeightsBySalesOrderItemReturnsWeights() {
        PieceWeightResponse weight = mock(PieceWeightResponse.class);
        when(purchaseOrderService.getPieceWeightsBySalesOrderItemId(1L)).thenReturn(List.of(weight));

        ApiResponse<List<PieceWeightResponse>> response = controller.pieceWeightsBySalesOrderItem(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).containsExactly(weight);
        verify(purchaseOrderService).getPieceWeightsBySalesOrderItemId(1L);
    }
}
