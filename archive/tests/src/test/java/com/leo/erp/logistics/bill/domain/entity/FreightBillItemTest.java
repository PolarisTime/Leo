package com.leo.erp.logistics.bill.domain.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FreightBillItemTest {

    @Test
    void shouldCreateEntityWithDefaultValues() {
        var entity = new FreightBillItem();
        assertThat(entity.getId()).isNull();
        assertThat(entity.getFreightBill()).isNull();
        assertThat(entity.getLineNo()).isNull();
        assertThat(entity.getSourceNo()).isNull();
        assertThat(entity.getCustomerName()).isNull();
        assertThat(entity.getProjectName()).isNull();
        assertThat(entity.getMaterialCode()).isNull();
        assertThat(entity.getMaterialName()).isNull();
        assertThat(entity.getBrand()).isNull();
        assertThat(entity.getCategory()).isNull();
        assertThat(entity.getMaterial()).isNull();
        assertThat(entity.getSpec()).isNull();
        assertThat(entity.getLength()).isNull();
        assertThat(entity.getQuantity()).isNull();
        assertThat(entity.getQuantityUnit()).isNull();
        assertThat(entity.getPieceWeightTon()).isNull();
        assertThat(entity.getPiecesPerBundle()).isNull();
        assertThat(entity.getBatchNo()).isNull();
        assertThat(entity.getWeightTon()).isNull();
        assertThat(entity.getWarehouseName()).isNull();
    }

    @Test
    void shouldSetAndGetId() {
        var entity = new FreightBillItem();
        entity.setId(1L);
        assertThat(entity.getId()).isEqualTo(1L);
    }

    @Test
    void shouldSetAndGetFreightBill() {
        var entity = new FreightBillItem();
        var bill = new FreightBill();
        bill.setId(100L);
        entity.setFreightBill(bill);
        assertThat(entity.getFreightBill()).isSameAs(bill);
        assertThat(entity.getFreightBill().getId()).isEqualTo(100L);
    }

    @Test
    void shouldSetAndGetLineNo() {
        var entity = new FreightBillItem();
        entity.setLineNo(1);
        assertThat(entity.getLineNo()).isEqualTo(1);
    }

    @Test
    void shouldSetAndGetSourceNo() {
        var entity = new FreightBillItem();
        entity.setSourceNo("SO-001");
        assertThat(entity.getSourceNo()).isEqualTo("SO-001");
    }

    @Test
    void shouldSetAndGetCustomerName() {
        var entity = new FreightBillItem();
        entity.setCustomerName("客户A");
        assertThat(entity.getCustomerName()).isEqualTo("客户A");
    }

    @Test
    void shouldSetAndGetProjectName() {
        var entity = new FreightBillItem();
        entity.setProjectName("项目A");
        assertThat(entity.getProjectName()).isEqualTo("项目A");
    }

    @Test
    void shouldSetAndGetMaterialCode() {
        var entity = new FreightBillItem();
        entity.setMaterialCode("M001");
        assertThat(entity.getMaterialCode()).isEqualTo("M001");
    }

    @Test
    void shouldSetAndGetMaterialName() {
        var entity = new FreightBillItem();
        entity.setMaterialName("钢材");
        assertThat(entity.getMaterialName()).isEqualTo("钢材");
    }

    @Test
    void shouldSetAndGetBrand() {
        var entity = new FreightBillItem();
        entity.setBrand("品牌A");
        assertThat(entity.getBrand()).isEqualTo("品牌A");
    }

    @Test
    void shouldSetAndGetCategory() {
        var entity = new FreightBillItem();
        entity.setCategory("类别A");
        assertThat(entity.getCategory()).isEqualTo("类别A");
    }

    @Test
    void shouldSetAndGetMaterial() {
        var entity = new FreightBillItem();
        entity.setMaterial("钢材");
        assertThat(entity.getMaterial()).isEqualTo("钢材");
    }

    @Test
    void shouldSetAndGetSpec() {
        var entity = new FreightBillItem();
        entity.setSpec("10mm");
        assertThat(entity.getSpec()).isEqualTo("10mm");
    }

    @Test
    void shouldSetAndGetLength() {
        var entity = new FreightBillItem();
        entity.setLength("6m");
        assertThat(entity.getLength()).isEqualTo("6m");
    }

    @Test
    void shouldSetAndGetQuantity() {
        var entity = new FreightBillItem();
        entity.setQuantity(100);
        assertThat(entity.getQuantity()).isEqualTo(100);
    }

    @Test
    void shouldSetAndGetQuantityUnit() {
        var entity = new FreightBillItem();
        entity.setQuantityUnit("件");
        assertThat(entity.getQuantityUnit()).isEqualTo("件");
    }

    @Test
    void shouldSetAndGetPieceWeightTon() {
        var entity = new FreightBillItem();
        entity.setPieceWeightTon(new BigDecimal("0.500"));
        assertThat(entity.getPieceWeightTon()).isEqualByComparingTo(new BigDecimal("0.500"));
    }

    @Test
    void shouldSetAndGetPiecesPerBundle() {
        var entity = new FreightBillItem();
        entity.setPiecesPerBundle(10);
        assertThat(entity.getPiecesPerBundle()).isEqualTo(10);
    }

    @Test
    void shouldSetAndGetBatchNo() {
        var entity = new FreightBillItem();
        entity.setBatchNo("BATCH-001");
        assertThat(entity.getBatchNo()).isEqualTo("BATCH-001");
    }

    @Test
    void shouldSetAndGetWeightTon() {
        var entity = new FreightBillItem();
        entity.setWeightTon(new BigDecimal("50.000"));
        assertThat(entity.getWeightTon()).isEqualByComparingTo(new BigDecimal("50.000"));
    }

    @Test
    void shouldSetAndGetWarehouseName() {
        var entity = new FreightBillItem();
        entity.setWarehouseName("仓库A");
        assertThat(entity.getWarehouseName()).isEqualTo("仓库A");
    }
}
