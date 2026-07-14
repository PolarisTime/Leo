package com.leo.erp.master.supplier.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.master.supplier.service.SupplierService;
import com.leo.erp.master.supplier.web.dto.SupplierOptionResponse;
import com.leo.erp.master.supplier.web.dto.SupplierRequest;
import com.leo.erp.master.supplier.web.dto.SupplierResponse;
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

class SupplierControllerTest {

    private final SupplierService supplierService = mock(SupplierService.class);
    private final SupplierController controller = new SupplierController(supplierService);

    @Test
    void optionsReturnsActiveSuppliers() {
        SupplierOptionResponse option = new SupplierOptionResponse(
                1L, 1L, "供应商A", "S001", "供应商A"
        );
        when(supplierService.listActiveOptions()).thenReturn(List.of(option));

        ApiResponse<List<SupplierOptionResponse>> response = controller.options();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).containsExactly(option);
        assertThat((Object) response.data().get(0).value()).isEqualTo(1L);
        verify(supplierService).listActiveOptions();
    }

    @Test
    void pageReturnsPaginatedSuppliers() {
        SupplierResponse supplier = mock(SupplierResponse.class);
        Page<SupplierResponse> page = new PageImpl<>(List.of(supplier));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(supplierService.page(any(), eq("test"), eq("active"))).thenReturn(page);

        ApiResponse<PageResponse<SupplierResponse>> response = controller.page(query, "test", "active");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsSupplierById() {
        SupplierResponse supplier = mock(SupplierResponse.class);
        when(supplierService.detail(1L)).thenReturn(supplier);

        ApiResponse<SupplierResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(supplier);
    }

    @Test
    void createReturnsCreatedSupplier() {
        SupplierRequest request = mock(SupplierRequest.class);
        SupplierResponse created = mock(SupplierResponse.class);
        when(supplierService.create(request)).thenReturn(created);

        ApiResponse<SupplierResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(supplierService).create(request);
    }

    @Test
    void updateReturnsUpdatedSupplier() {
        SupplierRequest request = mock(SupplierRequest.class);
        SupplierResponse updated = mock(SupplierResponse.class);
        when(supplierService.update(1L, request)).thenReturn(updated);

        ApiResponse<SupplierResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(supplierService).update(1L, request);
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(supplierService).delete(1L);
    }
}
