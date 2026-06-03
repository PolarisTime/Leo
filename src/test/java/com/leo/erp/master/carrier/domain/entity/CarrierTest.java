package com.leo.erp.master.carrier.domain.entity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierTest {

    @Test
    void shouldSetAndGetAllFields() {
        Carrier carrier = new Carrier();
        carrier.setId(1L);
        carrier.setCarrierCode("CR001");
        carrier.setCarrierName("物流甲方");
        carrier.setContactName("张三");
        carrier.setContactPhone("13800138000");
        carrier.setVehicleType("重型卡车");
        carrier.setPriceMode("按吨");
        carrier.setStatus("正常");
        carrier.setRemark("测试备注");

        assertThat(carrier.getId()).isEqualTo(1L);
        assertThat(carrier.getCarrierCode()).isEqualTo("CR001");
        assertThat(carrier.getCarrierName()).isEqualTo("物流甲方");
        assertThat(carrier.getContactName()).isEqualTo("张三");
        assertThat(carrier.getContactPhone()).isEqualTo("13800138000");
        assertThat(carrier.getVehicleType()).isEqualTo("重型卡车");
        assertThat(carrier.getPriceMode()).isEqualTo("按吨");
        assertThat(carrier.getStatus()).isEqualTo("正常");
        assertThat(carrier.getRemark()).isEqualTo("测试备注");
    }

    @Test
    void shouldInitializeEmptyVehiclesList() {
        Carrier carrier = new Carrier();
        assertThat(carrier.getVehicles()).isNotNull().isEmpty();
    }

    @Test
    void shouldManageVehiclesList() {
        Carrier carrier = new Carrier();
        Vehicle vehicle = new Vehicle();
        vehicle.setPlate("苏A12345");
        carrier.getVehicles().add(vehicle);

        assertThat(carrier.getVehicles()).hasSize(1);
        assertThat(carrier.getVehicles().get(0).getPlate()).isEqualTo("苏A12345");
    }
}
