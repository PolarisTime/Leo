package com.leo.erp.master.material.domain.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialTest {

    @Test
    void shouldSetAndGetAllFields() {
        Material material = new Material();
        material.setId(1L);
        material.setMaterialCode("MAT-001");
        material.setBrand("宝钢");
        material.setMaterial("Q235B");
        material.setCategory("板材");
        material.setSpec("10mm");
        material.setLength("6m");
        material.setUnit("吨");
        material.setQuantityUnit("支");
        material.setPieceWeightTon(new BigDecimal("1.234"));
        material.setPiecesPerBundle(12);
        material.setUnitPrice(new BigDecimal("500.50"));
        material.setBatchNoEnabled(true);
        material.setRemark("测试备注");

        assertThat(material.getId()).isEqualTo(1L);
        assertThat(material.getMaterialCode()).isEqualTo("MAT-001");
        assertThat(material.getBrand()).isEqualTo("宝钢");
        assertThat(material.getMaterial()).isEqualTo("Q235B");
        assertThat(material.getCategory()).isEqualTo("板材");
        assertThat(material.getSpec()).isEqualTo("10mm");
        assertThat(material.getLength()).isEqualTo("6m");
        assertThat(material.getUnit()).isEqualTo("吨");
        assertThat(material.getQuantityUnit()).isEqualTo("支");
        assertThat(material.getPieceWeightTon()).isEqualByComparingTo("1.234");
        assertThat(material.getPiecesPerBundle()).isEqualTo(12);
        assertThat(material.getUnitPrice()).isEqualByComparingTo("500.50");
        assertThat(material.getBatchNoEnabled()).isTrue();
        assertThat(material.getRemark()).isEqualTo("测试备注");
    }

    @Test
    void shouldHaveDefaultBatchNoEnabledAsFalse() {
        Material material = new Material();
        assertThat(material.getBatchNoEnabled()).isFalse();
    }

    @Test
    void shouldReturnNullForUnsetFields() {
        Material material = new Material();
        assertThat(material.getId()).isNull();
        assertThat(material.getMaterialCode()).isNull();
        assertThat(material.getBrand()).isNull();
        assertThat(material.getRemark()).isNull();
        assertThat(material.getLength()).isNull();
    }
}
