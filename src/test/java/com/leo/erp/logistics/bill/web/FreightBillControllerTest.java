package com.leo.erp.logistics.bill.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.logistics.bill.service.FreightBillService;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
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

class FreightBillControllerTest {

    private final FreightBillService service = mock(FreightBillService.class);
    private final FreightBillController controller = new FreightBillController(service);

    @Test
    void searchReturnsList() {
        FreightBillResponse item = mock(FreightBillResponse.class);
        when(service.search(eq("test"), eq(100))).thenReturn(List.of(item));

        ApiResponse<List<FreightBillResponse>> response = controller.search("test", 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void searchWithNullKeywordUsesEmptyString() {
        FreightBillResponse item = mock(FreightBillResponse.class);
        when(service.search(eq(""), eq(100))).thenReturn(List.of(item));

        ApiResponse<List<FreightBillResponse>> response = controller.search(null, 100);

        assertThat(response.code()).isEqualTo(0);
        verify(service).search("", 100);
    }

    @Test
    void searchClampsLimitToMax500() {
        when(service.search(eq("k"), eq(500))).thenReturn(List.of());

        ApiResponse<List<FreightBillResponse>> response = controller.search("k", 9999);

        assertThat(response.code()).isEqualTo(0);
        verify(service).search("k", 500);
    }

    @Test
    void pageReturnsPaginatedFreightBills() {
        FreightBillResponse item = mock(FreightBillResponse.class);
        Page<FreightBillResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(service.page(any(), any())).thenReturn(page);

        ApiResponse<PageResponse<FreightBillResponse>> response = controller.page(query, "test", "carrier", "active", null, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void pageWithNullFilters() {
        Page<FreightBillResponse> page = new PageImpl<>(List.of());
        PageQuery query = new PageQuery(0, 20, null, null);
        when(service.page(any(), any())).thenReturn(page);

        ApiResponse<PageResponse<FreightBillResponse>> response = controller.page(query, null, null, null, null, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).isEmpty();
    }

    @Test
    void detailReturnsFreightBillById() {
        FreightBillResponse bill = mock(FreightBillResponse.class);
        when(service.detail(1L)).thenReturn(bill);

        ApiResponse<FreightBillResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(bill);
    }

    @Test
    void createReturnsCreatedFreightBill() {
        FreightBillRequest request = mock(FreightBillRequest.class);
        FreightBillResponse created = mock(FreightBillResponse.class);
        when(service.create(request)).thenReturn(created);

        ApiResponse<FreightBillResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(service).create(request);
    }

    @Test
    void updateReturnsUpdatedFreightBill() {
        FreightBillRequest request = mock(FreightBillRequest.class);
        FreightBillResponse updated = mock(FreightBillResponse.class);
        when(service.update(1L, request)).thenReturn(updated);

        ApiResponse<FreightBillResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(service).update(1L, request);
    }

    @Test
    void updateStatusReturnsUpdatedFreightBill() {
        StatusUpdateRequest request = new StatusUpdateRequest("approved");
        FreightBillResponse updated = mock(FreightBillResponse.class);
        when(service.updateStatus(1L, "approved")).thenReturn(updated);

        ApiResponse<FreightBillResponse> response = controller.updateStatus(1L, request);

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
