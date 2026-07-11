package com.leo.erp.master.carrier.web.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierOptionResponseTest {

    @Test
    void shouldCreateRecord() {
        CarrierOptionResponse response = new CarrierOptionResponse(
                1L, "CR-001", "承运商A", "承运商A", List.of("沪A12345", "京B67890"), null, null
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.carrierCode()).isEqualTo("CR-001");
        assertThat(response.label()).isEqualTo("承运商A");
        assertThat(response.value()).isEqualTo("承运商A");
        assertThat(response.vehiclePlates()).containsExactly("沪A12345", "京B67890");
    }

    @Test
    void shouldSupportNullValues() {
        CarrierOptionResponse response = new CarrierOptionResponse(
                null, null, null, null
        );

        assertThat(response.id()).isNull();
        assertThat(response.label()).isNull();
        assertThat(response.value()).isNull();
        assertThat(response.vehiclePlates()).isNull();
    }

    @Test
    void shouldSupportEmptyVehiclePlates() {
        CarrierOptionResponse response = new CarrierOptionResponse(
                1L, "承运商A", "承运商A", List.of()
        );

        assertThat(response.vehiclePlates()).isEmpty();
    }

    @Test
    void shouldImplementEquals() {
        CarrierOptionResponse r1 = new CarrierOptionResponse(
                1L, "承运商A", "承运商A", List.of("沪A12345")
        );
        CarrierOptionResponse r2 = new CarrierOptionResponse(
                1L, "承运商A", "承运商A", List.of("沪A12345")
        );

        assertThat(r1).isEqualTo(r2);
    }

    @Test
    void shouldImplementHashCode() {
        CarrierOptionResponse r1 = new CarrierOptionResponse(
                1L, "承运商A", "承运商A", List.of("沪A12345")
        );
        CarrierOptionResponse r2 = new CarrierOptionResponse(
                1L, "承运商A", "承运商A", List.of("沪A12345")
        );

        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }
}
