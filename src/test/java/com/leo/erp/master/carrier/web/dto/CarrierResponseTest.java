package com.leo.erp.master.carrier.web.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierResponseTest {

    @Test
    void recordAccessors() {
        VehicleInfo vehicle = new VehicleInfo(1L, "京A12345", "张三", "13800138000", "备注");
        List<VehicleInfo> vehicles = List.of(vehicle);

        CarrierResponse response = new CarrierResponse(
                1L, "CR001", "承运商A", "李四", "13900139000", "货车",
                vehicles, "固定价格", "启用", "备注"
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.carrierCode()).isEqualTo("CR001");
        assertThat(response.carrierName()).isEqualTo("承运商A");
        assertThat(response.contactName()).isEqualTo("李四");
        assertThat(response.contactPhone()).isEqualTo("13900139000");
        assertThat(response.vehicleType()).isEqualTo("货车");
        assertThat(response.vehicles()).hasSize(1);
        assertThat(response.vehicles().get(0).plate()).isEqualTo("京A12345");
        assertThat(response.priceMode()).isEqualTo("固定价格");
        assertThat(response.status()).isEqualTo("启用");
        assertThat(response.remark()).isEqualTo("备注");
    }

    @Test
    void recordEquality() {
        VehicleInfo vehicle = new VehicleInfo(1L, "京A12345", "张三", "13800138000", "备注");
        List<VehicleInfo> vehicles = List.of(vehicle);

        CarrierResponse a = new CarrierResponse(
                1L, "CR001", "承运商A", "李四", "13900139000", "货车",
                vehicles, "固定价格", "启用", "备注"
        );
        CarrierResponse b = new CarrierResponse(
                1L, "CR001", "承运商A", "李四", "13900139000", "货车",
                vehicles, "固定价格", "启用", "备注"
        );

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void recordToString() {
        VehicleInfo vehicle = new VehicleInfo(1L, "京A12345", "张三", "13800138000", "备注");
        List<VehicleInfo> vehicles = List.of(vehicle);

        CarrierResponse response = new CarrierResponse(
                1L, "CR001", "承运商A", "李四", "13900139000", "货车",
                vehicles, "固定价格", "启用", "备注"
        );
        assertThat(response.toString()).contains("CR001", "承运商A");
    }
}