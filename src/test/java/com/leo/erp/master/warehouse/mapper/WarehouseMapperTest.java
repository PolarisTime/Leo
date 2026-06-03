package com.leo.erp.master.warehouse.mapper;

import com.leo.erp.master.warehouse.domain.entity.Warehouse;
import com.leo.erp.master.warehouse.web.dto.WarehouseResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseMapperTest {

    private final WarehouseMapper mapper = new WarehouseMapperImpl();

    @Test
    void shouldMapAllFieldsToResponse() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);
        warehouse.setWarehouseCode("WH001");
        warehouse.setWarehouseName("一号库");
        warehouse.setWarehouseType("室内");
        warehouse.setContactName("张三");
        warehouse.setContactPhone("13800138000");
        warehouse.setAddress("上海市浦东新区");
        warehouse.setStatus("正常");
        warehouse.setRemark("测试备注");

        WarehouseResponse response = mapper.toResponse(warehouse);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.warehouseCode()).isEqualTo("WH001");
        assertThat(response.warehouseName()).isEqualTo("一号库");
        assertThat(response.warehouseType()).isEqualTo("室内");
        assertThat(response.contactName()).isEqualTo("张三");
        assertThat(response.contactPhone()).isEqualTo("13800138000");
        assertThat(response.address()).isEqualTo("上海市浦东新区");
        assertThat(response.status()).isEqualTo("正常");
        assertThat(response.remark()).isEqualTo("测试备注");
    }

    @Test
    void shouldMapNullFieldsToNull() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);
        warehouse.setWarehouseCode("WH001");

        WarehouseResponse response = mapper.toResponse(warehouse);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.warehouseCode()).isEqualTo("WH001");
        assertThat(response.contactName()).isNull();
        assertThat(response.address()).isNull();
    }
}
