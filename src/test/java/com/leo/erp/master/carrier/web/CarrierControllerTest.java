package com.leo.erp.master.carrier.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.master.carrier.service.CarrierService;
import com.leo.erp.master.carrier.web.dto.CarrierOptionResponse;
import com.leo.erp.master.carrier.web.dto.CarrierRequest;
import com.leo.erp.master.carrier.web.dto.CarrierResponse;
import com.leo.erp.master.carrier.web.dto.VehicleOptionResponse;
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

class CarrierControllerTest {

    private final CarrierService carrierService = mock(CarrierService.class);
    private final CarrierController controller = new CarrierController(carrierService);

    @Test
    void optionsReturnsActiveCarriers() {
        CarrierOptionResponse option = new CarrierOptionResponse(
                1L,
                1L,
                "物流商A",
                "CR001",
                "物流商A",
                9L,
                "上海结算主体",
                List.of(new VehicleOptionResponse(101L, "苏A12345"))
        );
        when(carrierService.listActiveOptions()).thenReturn(List.of(option));

        ApiResponse<List<CarrierOptionResponse>> response = controller.options();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).containsExactly(option);
        assertThat((Object) response.data().get(0).value()).isEqualTo(1L);
        verify(carrierService).listActiveOptions();
    }

    @Test
    void pageReturnsPaginatedCarriers() {
        CarrierResponse carrier = mock(CarrierResponse.class);
        Page<CarrierResponse> page = new PageImpl<>(List.of(carrier));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(carrierService.page(any(), eq("test"), eq("active"))).thenReturn(page);

        ApiResponse<PageResponse<CarrierResponse>> response = controller.page(query, "test", "active");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsCarrierById() {
        CarrierResponse carrier = mock(CarrierResponse.class);
        when(carrierService.detail(1L)).thenReturn(carrier);

        ApiResponse<CarrierResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(carrier);
    }

    @Test
    void createReturnsCreatedCarrier() {
        CarrierRequest request = mock(CarrierRequest.class);
        CarrierResponse created = mock(CarrierResponse.class);
        when(carrierService.create(request)).thenReturn(created);

        ApiResponse<CarrierResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(carrierService).create(request);
    }

    @Test
    void updateReturnsUpdatedCarrier() {
        CarrierRequest request = mock(CarrierRequest.class);
        CarrierResponse updated = mock(CarrierResponse.class);
        when(carrierService.update(1L, request)).thenReturn(updated);

        ApiResponse<CarrierResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(carrierService).update(1L, request);
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(carrierService).delete(1L);
    }
}
