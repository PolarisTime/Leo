package com.leo.erp.master.carrier.mapper;

import com.leo.erp.master.carrier.domain.entity.Carrier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierMapperImplTest {

    private final CarrierMapper mapper = new CarrierMapperImpl();

    @Test
    void shouldReturnNull_whenCarrierIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void shouldReturnNull_whenVehicleIsNull() {
        assertThat(mapper.toVehicleInfo(null)).isNull();
    }

    @Test
    void shouldMapNullVehicles_whenCarrierVehiclesIsNull() {
        Carrier carrier = new Carrier();
        carrier.setId(1L);
        carrier.setCarrierCode("CR001");
        carrier.setCarrierName("物流甲");
        carrier.setVehicles(null);

        var response = mapper.toResponse(carrier);

        assertThat(response).isNotNull();
        assertThat(response.vehicles()).isNull();
    }
}
