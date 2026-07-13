package com.leo.erp.contract.purchase.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.contract.purchase.service.PurchaseContractService;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractRequest;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractResponse;
import com.leo.erp.security.permission.RequiresPermission;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.bind.annotation.PatchMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseContractControllerTest {

    private final PurchaseContractService purchaseContractService = mock(PurchaseContractService.class);
    private final PurchaseContractController controller = new PurchaseContractController(purchaseContractService);

    @Test
    void searchReturnsList() {
        PurchaseContractResponse item = mock(PurchaseContractResponse.class);
        when(purchaseContractService.search(eq("test"), eq(100))).thenReturn(List.of(item));

        ApiResponse<List<PurchaseContractResponse>> response = controller.search("test", 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void searchWithNullKeywordDefaultsToEmpty() {
        PurchaseContractResponse item = mock(PurchaseContractResponse.class);
        when(purchaseContractService.search(eq(""), eq(100))).thenReturn(List.of(item));

        ApiResponse<List<PurchaseContractResponse>> response = controller.search(null, 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
        verify(purchaseContractService).search("", 100);
    }

    @Test
    void pageReturnsPaginatedContracts() {
        PurchaseContractResponse item = mock(PurchaseContractResponse.class);
        Page<PurchaseContractResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(purchaseContractService.page(any(), any())).thenReturn(page);

        ApiResponse<PageResponse<PurchaseContractResponse>> response = controller.page(query, "test", "supplier", "active", null, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsContractById() {
        PurchaseContractResponse contract = mock(PurchaseContractResponse.class);
        when(purchaseContractService.detail(1L)).thenReturn(contract);

        ApiResponse<PurchaseContractResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(contract);
    }

    @Test
    void createReturnsCreatedContract() {
        PurchaseContractRequest request = mock(PurchaseContractRequest.class);
        PurchaseContractResponse created = mock(PurchaseContractResponse.class);
        when(purchaseContractService.create(request)).thenReturn(created);

        ApiResponse<PurchaseContractResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(purchaseContractService).create(request);
    }

    @Test
    void updateReturnsUpdatedContract() {
        PurchaseContractRequest request = mock(PurchaseContractRequest.class);
        PurchaseContractResponse updated = mock(PurchaseContractResponse.class);
        when(purchaseContractService.update(1L, request)).thenReturn(updated);

        ApiResponse<PurchaseContractResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(purchaseContractService).update(1L, request);
    }

    @Test
    void updateStatusUsesPatchRouteAndAuditPermission() throws ReflectiveOperationException {
        Method method = Arrays.stream(PurchaseContractController.class.getMethods())
                .filter(candidate -> candidate.getName().equals("updateStatus"))
                .findFirst()
                .orElse(null);

        assertThat(method).isNotNull();
        assertThat(method.getAnnotation(PatchMapping.class).value()).containsExactly("/{id}/status");
        RequiresPermission permission = method.getAnnotation(RequiresPermission.class);
        assertThat(permission.resource()).isEqualTo("purchase-contract");
        assertThat(permission.action()).isEqualTo("audit");

        PurchaseContractResponse updated = mock(PurchaseContractResponse.class);
        when(purchaseContractService.updateStatus(1L, StatusConstants.EXECUTING)).thenReturn(updated);
        @SuppressWarnings("unchecked")
        ApiResponse<PurchaseContractResponse> response = (ApiResponse<PurchaseContractResponse>) method.invoke(
                controller,
                1L,
                new StatusUpdateRequest(StatusConstants.EXECUTING)
        );

        assertThat(response.message()).isEqualTo("状态更新成功");
        assertThat(response.data()).isSameAs(updated);
        verify(purchaseContractService).updateStatus(1L, StatusConstants.EXECUTING);
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(purchaseContractService).delete(1L);
    }
}
