package com.leo.erp.contract.sales.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.contract.sales.service.SalesContractService;
import com.leo.erp.contract.sales.web.dto.SalesContractRequest;
import com.leo.erp.contract.sales.web.dto.SalesContractResponse;
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

class SalesContractControllerTest {

    private final SalesContractService salesContractService = mock(SalesContractService.class);
    private final SalesContractController controller = new SalesContractController(salesContractService);

    @Test
    void searchReturnsList() {
        SalesContractResponse item = mock(SalesContractResponse.class);
        when(salesContractService.search(eq("test"), eq(100))).thenReturn(List.of(item));

        ApiResponse<List<SalesContractResponse>> response = controller.search("test", 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void searchWithNullKeywordDefaultsToEmpty() {
        SalesContractResponse item = mock(SalesContractResponse.class);
        when(salesContractService.search(eq(""), eq(100))).thenReturn(List.of(item));

        ApiResponse<List<SalesContractResponse>> response = controller.search(null, 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
        verify(salesContractService).search("", 100);
    }

    @Test
    void pageReturnsPaginatedContracts() {
        SalesContractResponse item = mock(SalesContractResponse.class);
        Page<SalesContractResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(salesContractService.page(any(), any())).thenReturn(page);

        ApiResponse<PageResponse<SalesContractResponse>> response = controller.page(query, "test", "customer", "active", null, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsContractById() {
        SalesContractResponse contract = mock(SalesContractResponse.class);
        when(salesContractService.detail(1L)).thenReturn(contract);

        ApiResponse<SalesContractResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(contract);
    }

    @Test
    void createReturnsCreatedContract() {
        SalesContractRequest request = mock(SalesContractRequest.class);
        SalesContractResponse created = mock(SalesContractResponse.class);
        when(salesContractService.create(request)).thenReturn(created);

        ApiResponse<SalesContractResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(salesContractService).create(request);
    }

    @Test
    void updateReturnsUpdatedContract() {
        SalesContractRequest request = mock(SalesContractRequest.class);
        SalesContractResponse updated = mock(SalesContractResponse.class);
        when(salesContractService.update(1L, request)).thenReturn(updated);

        ApiResponse<SalesContractResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(salesContractService).update(1L, request);
    }

    @Test
    void updateStatusUsesPatchRouteAndAuditPermission() throws ReflectiveOperationException {
        Method method = Arrays.stream(SalesContractController.class.getMethods())
                .filter(candidate -> candidate.getName().equals("updateStatus"))
                .findFirst()
                .orElse(null);

        assertThat(method).isNotNull();
        assertThat(method.getAnnotation(PatchMapping.class).value()).containsExactly("/{id}/status");
        RequiresPermission permission = method.getAnnotation(RequiresPermission.class);
        assertThat(permission.resource()).isEqualTo("sales-contract");
        assertThat(permission.action()).isEqualTo("audit");

        SalesContractResponse updated = mock(SalesContractResponse.class);
        when(salesContractService.updateStatus(1L, StatusConstants.EXECUTING)).thenReturn(updated);
        @SuppressWarnings("unchecked")
        ApiResponse<SalesContractResponse> response = (ApiResponse<SalesContractResponse>) method.invoke(
                controller,
                1L,
                new StatusUpdateRequest(StatusConstants.EXECUTING)
        );

        assertThat(response.message()).isEqualTo("状态更新成功");
        assertThat(response.data()).isSameAs(updated);
        verify(salesContractService).updateStatus(1L, StatusConstants.EXECUTING);
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(salesContractService).delete(1L);
    }
}
