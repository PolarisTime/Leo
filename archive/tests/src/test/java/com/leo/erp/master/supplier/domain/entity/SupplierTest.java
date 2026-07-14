package com.leo.erp.master.supplier.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierTest {

    @Test
    void shouldSetAndGetAllFields() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setSupplierCode("S001");
        supplier.setSupplierName("供应商甲");
        supplier.setContactName("张三");
        supplier.setContactPhone("13800138000");
        supplier.setCity("北京");
        supplier.setStatus("正常");
        supplier.setRemark("测试备注");

        assertThat(supplier.getId()).isEqualTo(1L);
        assertThat(supplier.getSupplierCode()).isEqualTo("S001");
        assertThat(supplier.getSupplierName()).isEqualTo("供应商甲");
        assertThat(supplier.getContactName()).isEqualTo("张三");
        assertThat(supplier.getContactPhone()).isEqualTo("13800138000");
        assertThat(supplier.getCity()).isEqualTo("北京");
        assertThat(supplier.getStatus()).isEqualTo("正常");
        assertThat(supplier.getRemark()).isEqualTo("测试备注");
    }

    @Test
    void shouldReturnNullForUnsetFields() {
        Supplier supplier = new Supplier();
        assertThat(supplier.getId()).isNull();
        assertThat(supplier.getSupplierCode()).isNull();
        assertThat(supplier.getCity()).isNull();
    }
}
