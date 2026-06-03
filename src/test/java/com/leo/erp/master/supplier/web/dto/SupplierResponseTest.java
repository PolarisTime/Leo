package com.leo.erp.master.supplier.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierResponseTest {

    @Test
    void recordAccessors() {
        SupplierResponse response = new SupplierResponse(
                1L, "S001", "供应商A", "李四", "13900139000", "上海", "启用", "备注"
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.supplierCode()).isEqualTo("S001");
        assertThat(response.supplierName()).isEqualTo("供应商A");
        assertThat(response.contactName()).isEqualTo("李四");
        assertThat(response.contactPhone()).isEqualTo("13900139000");
        assertThat(response.city()).isEqualTo("上海");
        assertThat(response.status()).isEqualTo("启用");
        assertThat(response.remark()).isEqualTo("备注");
    }

    @Test
    void recordEquality() {
        SupplierResponse a = new SupplierResponse(
                1L, "S001", "供应商A", "李四", "13900139000", "上海", "启用", "备注"
        );
        SupplierResponse b = new SupplierResponse(
                1L, "S001", "供应商A", "李四", "13900139000", "上海", "启用", "备注"
        );

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void recordToString() {
        SupplierResponse response = new SupplierResponse(
                1L, "S001", "供应商A", "李四", "13900139000", "上海", "启用", "备注"
        );
        assertThat(response.toString()).contains("S001", "供应商A");
    }
}