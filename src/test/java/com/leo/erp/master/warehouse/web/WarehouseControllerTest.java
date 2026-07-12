package com.leo.erp.master.warehouse.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.master.warehouse.service.WarehouseService;
import com.leo.erp.master.warehouse.web.dto.WarehouseOptionResponse;
import com.leo.erp.master.warehouse.web.dto.WarehouseRequest;
import com.leo.erp.master.warehouse.web.dto.WarehouseResponse;
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

class WarehouseControllerTest {

    private final WarehouseService warehouseService = mock(WarehouseService.class);
    private final WarehouseController controller = new WarehouseController(warehouseService);

    @Test
    void optionsReturnsActiveWarehouses() {
        WarehouseOptionResponse option = new WarehouseOptionResponse(
                9007199254740993L,
                9007199254740993L,
                "W001 / 仓库A",
                "W001",
                "仓库A"
        );
        when(warehouseService.listActiveOptions()).thenReturn(List.of(option));

        ApiResponse<List<WarehouseOptionResponse>> response = controller.options();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).containsExactly(option);
    }

    @Test
    void pageReturnsPaginatedWarehouses() {
        WarehouseResponse warehouse = mock(WarehouseResponse.class);
        Page<WarehouseResponse> page = new PageImpl<>(List.of(warehouse));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(warehouseService.page(any(), eq("test"), eq("normal"), eq("active"))).thenReturn(page);

        ApiResponse<PageResponse<WarehouseResponse>> response = controller.page(query, "test", "normal", "active");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsWarehouseById() {
        WarehouseResponse warehouse = mock(WarehouseResponse.class);
        when(warehouseService.detail(1L)).thenReturn(warehouse);

        ApiResponse<WarehouseResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(warehouse);
    }

    @Test
    void createReturnsCreatedWarehouse() {
        WarehouseRequest request = mock(WarehouseRequest.class);
        WarehouseResponse created = mock(WarehouseResponse.class);
        when(warehouseService.create(request)).thenReturn(created);

        ApiResponse<WarehouseResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(warehouseService).create(request);
    }

    @Test
    void updateReturnsUpdatedWarehouse() {
        WarehouseRequest request = mock(WarehouseRequest.class);
        WarehouseResponse updated = mock(WarehouseResponse.class);
        when(warehouseService.update(1L, request)).thenReturn(updated);

        ApiResponse<WarehouseResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(warehouseService).update(1L, request);
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(warehouseService).delete(1L);
    }
}
