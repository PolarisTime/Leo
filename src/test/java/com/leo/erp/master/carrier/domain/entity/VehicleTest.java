package com.leo.erp.master.carrier.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleTest {

    @Test
    void shouldSetAndGetAllFields() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(1L);
        Carrier carrier = new Carrier();
        carrier.setId(10L);
        vehicle.setCarrier(carrier);
        vehicle.setPlate("苏A12345");
        vehicle.setContact("李四");
        vehicle.setPhone("13900139000");
        vehicle.setRemark("测试备注");
        vehicle.setSortOrder(1);

        assertThat(vehicle.getId()).isEqualTo(1L);
        assertThat(vehicle.getCarrier()).isEqualTo(carrier);
        assertThat(vehicle.getPlate()).isEqualTo("苏A12345");
        assertThat(vehicle.getContact()).isEqualTo("李四");
        assertThat(vehicle.getPhone()).isEqualTo("13900139000");
        assertThat(vehicle.getRemark()).isEqualTo("测试备注");
        assertThat(vehicle.getSortOrder()).isEqualTo(1);
    }

    @Test
    void shouldReturnNullForUnsetFields() {
        Vehicle vehicle = new Vehicle();
        assertThat(vehicle.getId()).isNull();
        assertThat(vehicle.getCarrier()).isNull();
        assertThat(vehicle.getPlate()).isNull();
        assertThat(vehicle.getContact()).isNull();
        assertThat(vehicle.getPhone()).isNull();
        assertThat(vehicle.getRemark()).isNull();
        assertThat(vehicle.getSortOrder()).isNull();
    }
}
