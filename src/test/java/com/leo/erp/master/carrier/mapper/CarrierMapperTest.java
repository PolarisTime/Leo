package com.leo.erp.master.carrier.mapper;

import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.domain.entity.Vehicle;
import com.leo.erp.master.carrier.web.dto.CarrierResponse;
import com.leo.erp.master.carrier.web.dto.VehicleInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierMapperTest {

    private final CarrierMapper mapper = new CarrierMapperImpl();

    @Test
    void shouldMapCarrierToResponseWithVehicles() {
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

        Vehicle v1 = new Vehicle();
        v1.setId(10L);
        v1.setPlate("苏A12345");
        v1.setContact("司机A");
        v1.setPhone("13900000001");
        v1.setRemark("车辆1");

        Vehicle v2 = new Vehicle();
        v2.setId(11L);
        v2.setPlate("苏A67890");
        v2.setContact("司机B");
        v2.setPhone("13900000002");
        v2.setRemark("车辆2");

        carrier.setVehicles(List.of(v1, v2));

        CarrierResponse response = mapper.toResponse(carrier);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.carrierCode()).isEqualTo("CR001");
        assertThat(response.carrierName()).isEqualTo("物流甲方");
        assertThat(response.contactName()).isEqualTo("张三");
        assertThat(response.contactPhone()).isEqualTo("13800138000");
        assertThat(response.vehicleType()).isEqualTo("重型卡车");
        assertThat(response.priceMode()).isEqualTo("按吨");
        assertThat(response.status()).isEqualTo("正常");
        assertThat(response.remark()).isEqualTo("测试备注");
        assertThat(response.vehicles()).hasSize(2);
        assertThat(response.vehicles().get(0).plate()).isEqualTo("苏A12345");
        assertThat(response.vehicles().get(1).plate()).isEqualTo("苏A67890");
    }

    @Test
    void shouldMapVehicleToVehicleInfo() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(1L);
        vehicle.setPlate("苏A12345");
        vehicle.setContact("张三");
        vehicle.setPhone("13800138000");
        vehicle.setRemark("测试备注");

        VehicleInfo info = mapper.toVehicleInfo(vehicle);

        assertThat(info.id()).isEqualTo(1L);
        assertThat(info.plate()).isEqualTo("苏A12345");
        assertThat(info.contact()).isEqualTo("张三");
        assertThat(info.phone()).isEqualTo("13800138000");
        assertThat(info.remark()).isEqualTo("测试备注");
    }

    @Test
    void shouldMapCarrierWithEmptyVehicles() {
        Carrier carrier = new Carrier();
        carrier.setId(1L);
        carrier.setCarrierCode("CR001");
        carrier.setCarrierName("物流甲方");

        CarrierResponse response = mapper.toResponse(carrier);

        assertThat(response.vehicles()).isEmpty();
    }
}
