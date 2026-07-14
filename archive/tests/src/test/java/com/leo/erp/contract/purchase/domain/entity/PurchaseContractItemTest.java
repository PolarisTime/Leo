package com.leo.erp.contract.purchase.domain.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseContractItemTest {

    @Test
    void shouldCreateEntityWithDefaultValues() {
        var entity = new PurchaseContractItem();
        assertThat(entity.getId()).isNull();
        assertThat(entity.getPurchaseContract()).isNull();
        assertThat(entity.getLineNo()).isNull();
        assertThat(entity.getMaterialCode()).isNull();
        assertThat(entity.getBrand()).isNull();
        assertThat(entity.getCategory()).isNull();
        assertThat(entity.getMaterial()).isNull();
        assertThat(entity.getSpec()).isNull();
        assertThat(entity.getLength()).isNull();
        assertThat(entity.getUnit()).isNull();
        assertThat(entity.getQuantity()).isNull();
        assertThat(entity.getQuantityUnit()).isNull();
        assertThat(entity.getPieceWeightTon()).isNull();
        assertThat(entity.getPiecesPerBundle()).isNull();
        assertThat(entity.getWeightTon()).isNull();
        assertThat(entity.getUnitPrice()).isNull();
        assertThat(entity.getAmount()).isNull();
    }

    @Test
    void shouldSetAndGetId() {
        var entity = new PurchaseContractItem();
        entity.setId(1L);
        assertThat(entity.getId()).isEqualTo(1L);
    }

    @Test
    void shouldSetAndGetPurchaseContract() {
        var entity = new PurchaseContractItem();
        var contract = new PurchaseContract();
        contract.setId(100L);
        entity.setPurchaseContract(contract);
        assertThat(entity.getPurchaseContract()).isSameAs(contract);
        assertThat(entity.getPurchaseContract().getId()).isEqualTo(100L);
    }

    @Test
    void shouldSetAndGetLineNo() {
        var entity = new PurchaseContractItem();
        entity.setLineNo(1);
        assertThat(entity.getLineNo()).isEqualTo(1);
    }

    @Test
    void shouldSetAndGetMaterialCode() {
        var entity = new PurchaseContractItem();
        entity.setMaterialCode("M001");
        assertThat(entity.getMaterialCode()).isEqualTo("M001");
    }

    @Test
    void shouldSetAndGetBrand() {
        var entity = new PurchaseContractItem();
        entity.setBrand("品牌A");
        assertThat(entity.getBrand()).isEqualTo("品牌A");
    }

    @Test
    void shouldSetAndGetCategory() {
        var entity = new PurchaseContractItem();
        entity.setCategory("类别A");
        assertThat(entity.getCategory()).isEqualTo("类别A");
    }

    @Test
    void shouldSetAndGetMaterial() {
        var entity = new PurchaseContractItem();
        entity.setMaterial("钢材");
        assertThat(entity.getMaterial()).isEqualTo("钢材");
    }

    @Test
    void shouldSetAndGetSpec() {
        var entity = new PurchaseContractItem();
        entity.setSpec("10mm");
        assertThat(entity.getSpec()).isEqualTo("10mm");
    }

    @Test
    void shouldSetAndGetLength() {
        var entity = new PurchaseContractItem();
        entity.setLength("6m");
        assertThat(entity.getLength()).isEqualTo("6m");
    }

    @Test
    void shouldSetAndGetUnit() {
        var entity = new PurchaseContractItem();
        entity.setUnit("吨");
        assertThat(entity.getUnit()).isEqualTo("吨");
    }

    @Test
    void shouldSetAndGetQuantity() {
        var entity = new PurchaseContractItem();
        entity.setQuantity(100);
        assertThat(entity.getQuantity()).isEqualTo(100);
    }

    @Test
    void shouldSetAndGetQuantityUnit() {
        var entity = new PurchaseContractItem();
        entity.setQuantityUnit("件");
        assertThat(entity.getQuantityUnit()).isEqualTo("件");
    }

    @Test
    void shouldSetAndGetPieceWeightTon() {
        var entity = new PurchaseContractItem();
        entity.setPieceWeightTon(new BigDecimal("0.500"));
        assertThat(entity.getPieceWeightTon()).isEqualByComparingTo(new BigDecimal("0.500"));
    }

    @Test
    void shouldSetAndGetPiecesPerBundle() {
        var entity = new PurchaseContractItem();
        entity.setPiecesPerBundle(10);
        assertThat(entity.getPiecesPerBundle()).isEqualTo(10);
    }

    @Test
    void shouldSetAndGetWeightTon() {
        var entity = new PurchaseContractItem();
        entity.setWeightTon(new BigDecimal("50.000"));
        assertThat(entity.getWeightTon()).isEqualByComparingTo(new BigDecimal("50.000"));
    }

    @Test
    void shouldSetAndGetUnitPrice() {
        var entity = new PurchaseContractItem();
        entity.setUnitPrice(new BigDecimal("3000.00"));
        assertThat(entity.getUnitPrice()).isEqualByComparingTo(new BigDecimal("3000.00"));
    }

    @Test
    void shouldSetAndGetAmount() {
        var entity = new PurchaseContractItem();
        entity.setAmount(new BigDecimal("150000.00"));
        assertThat(entity.getAmount()).isEqualByComparingTo(new BigDecimal("150000.00"));
    }
}
