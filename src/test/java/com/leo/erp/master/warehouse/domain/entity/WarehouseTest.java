package com.leo.erp.master.warehouse.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseTest {

    @Test
    void shouldSetAndGetAllFields() {
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

        assertThat(warehouse.getId()).isEqualTo(1L);
        assertThat(warehouse.getWarehouseCode()).isEqualTo("WH001");
        assertThat(warehouse.getWarehouseName()).isEqualTo("一号库");
        assertThat(warehouse.getWarehouseType()).isEqualTo("室内");
        assertThat(warehouse.getContactName()).isEqualTo("张三");
        assertThat(warehouse.getContactPhone()).isEqualTo("13800138000");
        assertThat(warehouse.getAddress()).isEqualTo("上海市浦东新区");
        assertThat(warehouse.getStatus()).isEqualTo("正常");
        assertThat(warehouse.getRemark()).isEqualTo("测试备注");
    }

    @Test
    void shouldReturnNullForUnsetFields() {
        Warehouse warehouse = new Warehouse();
        assertThat(warehouse.getId()).isNull();
        assertThat(warehouse.getWarehouseCode()).isNull();
        assertThat(warehouse.getContactName()).isNull();
    }
}
