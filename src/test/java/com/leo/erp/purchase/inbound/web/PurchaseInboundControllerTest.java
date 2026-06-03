package com.leo.erp.purchase.inbound.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.purchase.inbound.service.PurchaseInboundService;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundResponse;
import com.leo.erp.purchase.order.web.dto.PieceWeightResponse;
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

class PurchaseInboundControllerTest {

    private final PurchaseInboundService service = mock(PurchaseInboundService.class);
    private final PurchaseInboundController controller = new PurchaseInboundController(service);

    @Test
    void searchReturnsList() {
        PurchaseInboundResponse item = mock(PurchaseInboundResponse.class);
        when(service.search(eq("test"), eq(100))).thenReturn(List.of(item));

        ApiResponse<List<PurchaseInboundResponse>> response = controller.search("test", 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void searchWithNullKeywordDefaultsToEmpty() {
        PurchaseInboundResponse item = mock(PurchaseInboundResponse.class);
        when(service.search(eq(""), eq(100))).thenReturn(List.of(item));

        ApiResponse<List<PurchaseInboundResponse>> response = controller.search(null, 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
        verify(service).search("", 100);
    }

    @Test
    void pageReturnsPaginatedPurchaseInbounds() {
        PurchaseInboundResponse item = mock(PurchaseInboundResponse.class);
        Page<PurchaseInboundResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(service.page(any(), any())).thenReturn(page);

        ApiResponse<PageResponse<PurchaseInboundResponse>> response = controller.page(query, "test", "supplier", "active", null, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsPurchaseInboundById() {
        PurchaseInboundResponse inbound = mock(PurchaseInboundResponse.class);
        when(service.detail(1L)).thenReturn(inbound);

        ApiResponse<PurchaseInboundResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(inbound);
    }

    @Test
    void pieceWeightsReturnsList() {
        PieceWeightResponse item = mock(PieceWeightResponse.class);
        when(service.getPieceWeights(1L)).thenReturn(List.of(item));

        ApiResponse<List<PieceWeightResponse>> response = controller.pieceWeights(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void createReturnsCreatedPurchaseInbound() {
        PurchaseInboundRequest request = mock(PurchaseInboundRequest.class);
        PurchaseInboundResponse created = mock(PurchaseInboundResponse.class);
        when(service.create(request)).thenReturn(created);

        ApiResponse<PurchaseInboundResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(service).create(request);
    }

    @Test
    void updateReturnsUpdatedPurchaseInbound() {
        PurchaseInboundRequest request = mock(PurchaseInboundRequest.class);
        PurchaseInboundResponse updated = mock(PurchaseInboundResponse.class);
        when(service.update(1L, request)).thenReturn(updated);

        ApiResponse<PurchaseInboundResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(service).update(1L, request);
    }

    @Test
    void updateStatusReturnsUpdatedPurchaseInbound() {
        StatusUpdateRequest request = new StatusUpdateRequest("approved");
        PurchaseInboundResponse updated = mock(PurchaseInboundResponse.class);
        when(service.updateStatus(1L, "approved")).thenReturn(updated);

        ApiResponse<PurchaseInboundResponse> response = controller.updateStatus(1L, request);

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
