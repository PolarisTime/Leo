package com.leo.erp.master.warehouse.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseResponseTest {

    @Test
    void recordAccessors() {
        WarehouseResponse response = new WarehouseResponse(
                1L, "W001", "主仓库", "成品库", "王五", "13700137000", "北京市海淀区", "启用", "备注"
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.warehouseCode()).isEqualTo("W001");
        assertThat(response.warehouseName()).isEqualTo("主仓库");
        assertThat(response.warehouseType()).isEqualTo("成品库");
        assertThat(response.contactName()).isEqualTo("王五");
        assertThat(response.contactPhone()).isEqualTo("13700137000");
        assertThat(response.address()).isEqualTo("北京市海淀区");
        assertThat(response.status()).isEqualTo("启用");
        assertThat(response.remark()).isEqualTo("备注");
    }

    @Test
    void recordEquality() {
        WarehouseResponse a = new WarehouseResponse(
                1L, "W001", "主仓库", "成品库", "王五", "13700137000", "北京市海淀区", "启用", "备注"
        );
        WarehouseResponse b = new WarehouseResponse(
                1L, "W001", "主仓库", "成品库", "王五", "13700137000", "北京市海淀区", "启用", "备注"
        );

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void recordToString() {
        WarehouseResponse response = new WarehouseResponse(
                1L, "W001", "主仓库", "成品库", "王五", "13700137000", "北京市海淀区", "启用", "备注"
        );
        assertThat(response.toString()).contains("W001", "主仓库");
    }
}