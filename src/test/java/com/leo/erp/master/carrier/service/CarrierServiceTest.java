package com.leo.erp.master.carrier.service;

import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.domain.entity.Vehicle;
import com.leo.erp.master.carrier.mapper.CarrierMapper;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.master.carrier.repository.VehicleRepository;
import com.leo.erp.master.carrier.web.dto.CarrierOptionResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CarrierServiceTest {

    @Test
    void shouldReturnCarrierOptionsWithVehiclePlates() {
        CarrierRepository repository = mock(CarrierRepository.class);
        VehicleRepository vehicleRepository = mock(VehicleRepository.class);
        CarrierMapper mapper = mock(CarrierMapper.class);
        Carrier carrier = new Carrier();
        carrier.setId(1L);
        carrier.setCarrierCode("WL-001");
        carrier.setCarrierName("物流甲");

        Vehicle v1 = new Vehicle();
        v1.setPlate("苏A12345");
        v1.setCarrier(carrier);
        Vehicle v2 = new Vehicle();
        v2.setPlate("苏A67890");
        v2.setCarrier(carrier);
        Vehicle v3 = new Vehicle();
        v3.setPlate("苏A99999");
        v3.setCarrier(carrier);
        carrier.setVehicles(List.of(v1, v2, v3));

        when(repository.findByDeletedFlagFalseAndStatusOrderByCarrierCodeAsc(com.leo.erp.common.support.StatusConstants.NORMAL)).thenReturn(List.of(carrier));

        CarrierService service = new CarrierService(repository, vehicleRepository, null, mapper);

        List<CarrierOptionResponse> options = service.listActiveOptions();

        assertThat(options).singleElement().satisfies(option -> {
            assertThat(option.id()).isEqualTo(1L);
            assertThat(option.label()).isEqualTo("物流甲");
            assertThat(option.value()).isEqualTo("物流甲");
            assertThat(option.vehiclePlates()).containsExactly("苏A12345", "苏A67890", "苏A99999");
        });
    }
}
