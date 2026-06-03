package com.leo.erp.sales.outbound.domain.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOutboundItemTest {

    @Test
    void shouldSetAndGetAllFields() {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(1L);

        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(10L);
        item.setSalesOutbound(outbound);
        item.setLineNo(1);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("盘螺");
        item.setMaterial("HRB400");
        item.setSpec("8");
        item.setLength("12m");
        item.setUnit("吨");
        item.setSourceSalesOrderItemId(201L);
        item.setWarehouseName("一号库");
        item.setBatchNo("B1");
        item.setQuantity(5);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("2.248"));
        item.setPiecesPerBundle(2);
        item.setWeightTon(new BigDecimal("11.240"));
        item.setUnitPrice(new BigDecimal("3000.00"));
        item.setAmount(new BigDecimal("33720.00"));

        assertThat(item.getId()).isEqualTo(10L);
        assertThat(item.getSalesOutbound()).isEqualTo(outbound);
        assertThat(item.getLineNo()).isEqualTo(1);
        assertThat(item.getMaterialCode()).isEqualTo("M1");
        assertThat(item.getBrand()).isEqualTo("宝钢");
        assertThat(item.getCategory()).isEqualTo("盘螺");
        assertThat(item.getMaterial()).isEqualTo("HRB400");
        assertThat(item.getSpec()).isEqualTo("8");
        assertThat(item.getLength()).isEqualTo("12m");
        assertThat(item.getUnit()).isEqualTo("吨");
        assertThat(item.getSourceSalesOrderItemId()).isEqualTo(201L);
        assertThat(item.getWarehouseName()).isEqualTo("一号库");
        assertThat(item.getBatchNo()).isEqualTo("B1");
        assertThat(item.getQuantity()).isEqualTo(5);
        assertThat(item.getQuantityUnit()).isEqualTo("件");
        assertThat(item.getPieceWeightTon()).isEqualByComparingTo("2.248");
        assertThat(item.getPiecesPerBundle()).isEqualTo(2);
        assertThat(item.getWeightTon()).isEqualByComparingTo("11.240");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("3000.00");
        assertThat(item.getAmount()).isEqualByComparingTo("33720.00");
    }
}
