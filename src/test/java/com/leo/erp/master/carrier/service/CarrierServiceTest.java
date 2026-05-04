package com.leo.erp.master.carrier.service;

import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.mapper.CarrierMapper;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.master.carrier.web.dto.CarrierOptionResponse;
import com.leo.erp.master.carrier.web.dto.CarrierResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CarrierServiceTest {

    @Test
    void shouldReturnCarrierOptionsWithVehiclePlates() {
        CarrierRepository repository = mock(CarrierRepository.class);
        CarrierMapper mapper = mock(CarrierMapper.class);
        Carrier carrier = new Carrier();
        carrier.setId(1L);
        when(repository.findByDeletedFlagFalseOrderByCarrierCodeAsc()).thenReturn(List.of(carrier));
        when(mapper.toResponse(carrier)).thenReturn(new CarrierResponse(
                1L,
                "WL-001",
                "物流甲",
                null,
                null,
                null,
                "[{\"plate\":\"苏A12345\"},{\"plate\":\"苏A99999\"}]",
                "苏A12345",
                null,
                null,
                "苏A67890",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "正常",
                null
        ));

        CarrierService service = new CarrierService(repository, null, mapper);

        List<CarrierOptionResponse> options = service.listActiveOptions();

        assertThat(options).singleElement().satisfies(option -> {
            assertThat(option.label()).isEqualTo("物流甲");
            assertThat(option.value()).isEqualTo("物流甲");
            assertThat(option.vehiclePlates()).containsExactly("苏A12345", "苏A67890", "苏A99999");
        });
    }
}
